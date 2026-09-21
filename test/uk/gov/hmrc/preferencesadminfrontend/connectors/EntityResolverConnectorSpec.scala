/*
 * Copyright 2026 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.preferencesadminfrontend.connectors

import com.github.tomakehurst.wiremock.client.WireMock
import play.api.http.Status
import play.api.libs.json.*
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.preferencesadminfrontend.services.model.{ Email, EntityId, TaxIdentifier }
import uk.gov.hmrc.preferencesadminfrontend.utils.ConnectorBaseSpec

import java.time.{ ZoneOffset, ZonedDateTime }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random

class EntityResolverConnectorSpec extends ConnectorBaseSpec(EntityResolverConnector.configKey) {

  class TestCase {
    val sautr = TaxIdentifier("sautr", Random.nextInt(1000000).toString)
    val nino = TaxIdentifier("nino", "NA000914D")
    val itsaId = TaxIdentifier("HMRC-MTD-IT", "XYIT00000067034")
    val entityId: String = Random.nextInt(1000000).toString

    implicit val hc: HeaderCarrier = HeaderCarrier()

    val entityResolverConnector: EntityResolverConnector = app.injector.instanceOf[EntityResolverConnector]

    def taxIdentifiersResponseFor(taxIds: TaxIdentifier*) = {
      val taxIdsJson: Seq[(String, JsValue)] = taxIds.map { case TaxIdentifier(name, value) =>
        name -> JsString(value)
      }
      taxIdsJson.foldLeft(Json.obj("_id" -> "6a048719-3d4b-4a3e-9440-17b238807bc9"))(_ + _)
    }

  }

  "getTaxIdentifiers" must {

    def stubGetTaxIdentifiers(taxIdentifier: TaxIdentifier, statusCode: Int, response: String): Unit =
      wireMockServer.stubFor(
        WireMock
          .get(WireMock.urlPathEqualTo(s"/entity-resolver"))
          .withQueryParam("taxRegime", WireMock.equalTo(taxIdentifier.regime))
          .withQueryParam("taxId", WireMock.equalTo(taxIdentifier.value))
          .willReturn(
            WireMock
              .aResponse()
              .withStatus(statusCode)
              .withBody(response)
          )
      )

    "return only sautr if nino does not exist" in new TestCase {
      val responseJson = taxIdentifiersResponseFor(sautr)
      stubGetTaxIdentifiers(sautr, Status.OK, responseJson.toString)

      val result = entityResolverConnector.getTaxIdentifiers(sautr).futureValue
      result mustBe List(sautr)
    }

    "return all tax identifiers for sautr" in new TestCase {
      val responseJson = taxIdentifiersResponseFor(sautr, nino)
      stubGetTaxIdentifiers(sautr, Status.OK, responseJson.toString)

      val result = entityResolverConnector.getTaxIdentifiers(sautr).futureValue
      result mustBe List(sautr, nino)
    }

    "return all tax identifiers for nino" in new TestCase {
      val responseJson = taxIdentifiersResponseFor(sautr, nino)
      stubGetTaxIdentifiers(nino, Status.OK, responseJson.toString)

      val result = entityResolverConnector.getTaxIdentifiers(nino).futureValue
      result mustBe List(sautr, nino)
    }

    "return all tax identifiers for nino along with itsaId" in new TestCase {
      val responseJson = taxIdentifiersResponseFor(sautr, nino, itsaId)
      stubGetTaxIdentifiers(nino, Status.OK, responseJson.toString)

      val result = entityResolverConnector.getTaxIdentifiers(nino).futureValue
      result mustBe List(sautr, nino, itsaId)
    }

    "return all tax identifiers for sautr along with itsaId" in new TestCase {
      val responseJson = taxIdentifiersResponseFor(sautr, nino, itsaId)
      stubGetTaxIdentifiers(sautr, Status.OK, responseJson.toString)

      val result = entityResolverConnector.getTaxIdentifiers(sautr).futureValue
      result mustBe List(sautr, nino, itsaId)
    }

    "return all tax identifiers for sautr along with itsaId when sautr entered with spaces" in new TestCase {
      val responseJson = taxIdentifiersResponseFor(sautr, nino, itsaId)
      val sautrWithSpaces = TaxIdentifier("sautr", s" ${sautr.value} ")
      stubGetTaxIdentifiers(sautrWithSpaces.copy(value = sautrWithSpaces.value.trim), Status.OK, responseJson.toString)

      val result = entityResolverConnector
        .getTaxIdentifiers(sautrWithSpaces)
        .futureValue

      result mustBe List(sautr, nino, itsaId)
    }

    "return all tax identifiers for itsaId when itsaId entered with spaces & special chars" in new TestCase {
      val responseJson = taxIdentifiersResponseFor(sautr, nino, itsaId)
      val itsaIdWithSpaces = TaxIdentifier("HMRC-MTD-IT", s" HMRC-MTD-IT~ITSAID~XYIT00000067034 ")
      stubGetTaxIdentifiers(
        itsaIdWithSpaces.copy(value = itsaIdWithSpaces.value.trim),
        Status.OK,
        responseJson.toString
      )

      val result = entityResolverConnector
        .getTaxIdentifiers(itsaIdWithSpaces)
        .futureValue

      result mustBe List(sautr, nino, itsaId)
    }

    "return empty sequence when not found" in new TestCase {
      stubGetTaxIdentifiers(nino, Status.NOT_FOUND, "")

      val result = entityResolverConnector
        .getTaxIdentifiers(nino)
        .futureValue

      result mustBe empty
    }

    "return empty sequence if Entity-Resolver returns 400" in new TestCase {
      stubGetTaxIdentifiers(nino, Status.BAD_REQUEST, "")

      val result = entityResolverConnector.getTaxIdentifiers(nino).futureValue

      result mustBe empty
    }

    "handle unexpected exceptions" in new TestCase {
      stubGetTaxIdentifiers(sautr, Status.INTERNAL_SERVER_ERROR, "")

      val result = entityResolverConnector.getTaxIdentifiers(sautr).futureValue
      result mustBe empty
    }
  }

  "getTaxIdentifiers overloaded with PreferenceDetails" must {
    def stubGetTaxIdentifiersForEntityId(entityId: String, statusCode: Int, response: String): Unit =
      wireMockServer.stubFor(
        WireMock
          .get(WireMock.urlPathEqualTo(s"/entity-resolver"))
          .withQueryParam("entityId", WireMock.equalTo(entityId))
          .willReturn(
            WireMock
              .aResponse()
              .withStatus(statusCode)
              .withBody(response)
          )
      )

    def mockPreferenceDetailsForGetTaxIdentifiers(id: String): PreferenceDetails =
      PreferenceDetails(
        genericPaperless = true,
        genericUpdatedAt = None,
        isPaperless = None,
        email = None,
        entityId = Some(EntityId(id)),
        eventType = None,
        viaMobileApp = None
      )

    "construct the correct URL using entityId and return identifiers" in new TestCase {
      val mockPreferenceDetails: PreferenceDetails = mockPreferenceDetailsForGetTaxIdentifiers(entityId)

      val responseJson: JsObject = taxIdentifiersResponseFor(sautr, nino)
      stubGetTaxIdentifiersForEntityId(entityId, Status.OK, responseJson.toString)

      val result: Seq[TaxIdentifier] =
        entityResolverConnector.getTaxIdentifiers(mockPreferenceDetails).futureValue

      result mustBe List(sautr, nino)
    }

    "return empty sequence if the service returns BAD_REQUEST" in new TestCase {
      val mockPreferenceDetails: PreferenceDetails = mockPreferenceDetailsForGetTaxIdentifiers(entityId)
      stubGetTaxIdentifiersForEntityId(entityId, Status.BAD_REQUEST, "")

      val result: Seq[TaxIdentifier] =
        entityResolverConnector.getTaxIdentifiers(mockPreferenceDetails).futureValue

      result mustBe empty
    }

    "return empty sequence if the service returns CONFLICT" in new TestCase {
      val mockPreferenceDetails: PreferenceDetails = mockPreferenceDetailsForGetTaxIdentifiers(entityId)
      stubGetTaxIdentifiersForEntityId(entityId, Status.CONFLICT, "")

      val result: Seq[TaxIdentifier] =
        entityResolverConnector.getTaxIdentifiers(mockPreferenceDetails).futureValue

      result mustBe empty
    }

    "return empty sequence if the service returns 404" in new TestCase {
      val mockPreferenceDetails: PreferenceDetails = mockPreferenceDetailsForGetTaxIdentifiers(entityId)
      stubGetTaxIdentifiersForEntityId(entityId, Status.NOT_FOUND, "")

      val result: Seq[TaxIdentifier] =
        entityResolverConnector.getTaxIdentifiers(mockPreferenceDetails).futureValue

      result mustBe empty
    }
  }

  "confirm" must {
    def stubConfirm(entityId: String, itsaId: String, statusCode: Int, responseBody: String): Unit =
      wireMockServer.stubFor(
        WireMock
          .post(WireMock.urlPathEqualTo(s"/preferences/confirm/$entityId/$itsaId"))
          .willReturn(
            WireMock
              .aResponse()
              .withStatus(statusCode)
              .withBody(responseBody)
          )
      )

    "return Right if the status is Successful (200)" in new TestCase {
      stubConfirm(entityId, itsaId.name, Status.OK, "")

      entityResolverConnector.confirm(entityId, itsaId.name).futureValue mustBe Right(())
    }

    "return Left if status is 500" in new TestCase {
      stubConfirm(entityId, itsaId.name, Status.INTERNAL_SERVER_ERROR, "ErrorBody")

      entityResolverConnector.confirm(entityId, itsaId.name).futureValue mustBe Left(
        "upstream error when confirming ITSA preference, 500 ErrorBody"
      )
    }
  }

  "getPreferenceDetails" must {
    def stubGetPreferenceDetails(regime: String, value: String, statusCode: Int, responseBody: String): Unit =
      wireMockServer.stubFor(
        WireMock
          .get(WireMock.urlPathEqualTo(s"/portal/preferences/$regime/$value"))
          .willReturn(
            WireMock
              .aResponse()
              .withStatus(statusCode)
              .withBody(responseBody)
          )
      )

    def preferenceDetailsResponseForGenericOptedIn(emailVerified: Boolean): JsValue = {
      val genericUpdatedAt = 1518652800000L
      val genericUpdatedAtStr = s""" "updatedAt": $genericUpdatedAt """
      val verifiedOnDate = 1518652800000L
      val verifiedOnDateStr = if (emailVerified) s""" "verifiedOn": $verifiedOnDate, """ else ""
      Json.parse(s"""
                    |{
                    |  "digital": true,
                    |  "termsAndConditions": {
                    |    "generic": {
                    |      "accepted": true,
                    |      $genericUpdatedAtStr
                    |    }
                    |  },
                    |  "email": {
                    |    "email": "john.doe@digital.hmrc.gov.uk",
                    |    "status": "${if (emailVerified) "verified" else ""}",
                    |    $verifiedOnDateStr
                    |    "mailboxFull": false,
                    |    "hasBounces":false
                    |  }
                    |}
       """.stripMargin)
    }

    val verfiedOn = Some(ZonedDateTime.of(2018, 2, 15, 0, 0, 0, 0, ZoneOffset.UTC))

    "return generic paperless preference true and valid email address and verification true if user is opted in for saUtr" in new TestCase {
      val responseJson = preferenceDetailsResponseForGenericOptedIn(true)
      stubGetPreferenceDetails(sautr.regime, sautr.value, Status.OK, responseJson.toString)

      val result: Option[PreferenceDetails] =
        entityResolverConnector.getPreferenceDetails(sautr).futureValue

      result mustBe defined
      result.get.genericPaperless mustBe true
      result.get.email.get.address mustBe "john.doe@digital.hmrc.gov.uk"
      result.get.email.get.verified mustBe true
      result.get.email.get.verifiedOn.get.isEqual(verfiedOn.get) mustBe true
    }

    "return generic paperless preference true and valid email address and verification false if user is opted in for saUtr" in new TestCase {
      val responseJson = preferenceDetailsResponseForGenericOptedIn(false)
      stubGetPreferenceDetails(sautr.regime, sautr.value, Status.OK, responseJson.toString)

      val result = entityResolverConnector.getPreferenceDetails(sautr).futureValue

      result mustBe defined
      result.get.genericPaperless mustBe true
      result.get.email mustBe Some(Email("john.doe@digital.hmrc.gov.uk", false, None, None, false, None))
    }

    "return generic paperless preference false and email as 'None' if user is opted out for saUtr" in new TestCase {
      val responseJson = Json.parse("""
                                      |{
                                      |  "digital": false,
                                      |   "termsAndConditions": {
                                      |    "generic": {
                                      |      "accepted": false,
                                      |       "updatedAt": 1518652800000
                                      |    }
                                      |  }
                                      |}
                                      |         """.stripMargin)

      stubGetPreferenceDetails(sautr.regime, sautr.value, Status.OK, responseJson.toString)

      val result =
        entityResolverConnector.getPreferenceDetails(sautr).futureValue

      result mustBe defined
      result.get.genericPaperless mustBe false
      result.get.email mustBe None
    }

    "return email address and verification if user is opted in for nino" in new TestCase {
      val responseJson = preferenceDetailsResponseForGenericOptedIn(true)
      stubGetPreferenceDetails(nino.regime, nino.value, Status.OK, responseJson.toString)

      val result = entityResolverConnector.getPreferenceDetails(nino).futureValue

      result mustBe defined
      result.get.genericPaperless mustBe true
      result.get.email.get.address mustBe "john.doe@digital.hmrc.gov.uk"
      result.get.email.get.verified mustBe true
      result.get.email.get.verifiedOn.get.isEqual(verfiedOn.get) mustBe true
    }

    "return email address and verification if user is opted in for nino when nino entered with spaces" in new TestCase {
      val responseJson = preferenceDetailsResponseForGenericOptedIn(true)
      stubGetPreferenceDetails(nino.regime, nino.value, Status.OK, responseJson.toString)
      val ninoValue = TaxIdentifier("nino", "NA000914 D ")

      val result = entityResolverConnector.getPreferenceDetails(nino).futureValue

      result mustBe defined
      result.get.genericPaperless mustBe true
      result.get.email.get.address mustBe "john.doe@digital.hmrc.gov.uk"
      result.get.email.get.verified mustBe true
      result.get.email.get.verifiedOn.get.isEqual(verfiedOn.get) mustBe true
    }

    "return None if taxId does not exist" in new TestCase {
      stubGetPreferenceDetails(nino.regime, nino.value, Status.NOT_FOUND, "")

      val result = entityResolverConnector.getPreferenceDetails(sautr).futureValue

      result mustBe None
    }

    "return None if taxId is malformed" in new TestCase {
      stubGetPreferenceDetails(nino.regime, nino.value, Status.BAD_REQUEST, "")
      val result = entityResolverConnector.getPreferenceDetails(nino).futureValue
      result mustBe None
    }

    "handle unexpected exceptions" in new TestCase {
      stubGetPreferenceDetails(nino.regime, nino.value, Status.INTERNAL_SERVER_ERROR, "")
      val result = entityResolverConnector.getPreferenceDetails(sautr).futureValue
      result mustBe None
    }

  }

  "optOut" must {
    def stubOptOut(taxIdentifier: TaxIdentifier, statusCode: Int, response: String): Unit =
      wireMockServer.stubFor(
        WireMock
          .post(
            WireMock.urlPathEqualTo(
              s"/entity-resolver-admin/manual-opt-out/${taxIdentifier.regime}/${taxIdentifier.value}"
            )
          )
          .willReturn(
            WireMock
              .aResponse()
              .withStatus(statusCode)
              .withBody(response)
          )
      )

    "return true if status is OK (user is opted out)" in new TestCase {
      stubOptOut(sautr, Status.OK, "")
      val result = entityResolverConnector.optOut(sautr).futureValue

      result mustBe OptedOut
    }

    "return false if CONFLICT" in new TestCase {
      stubOptOut(sautr, Status.CONFLICT, "")
      val result = entityResolverConnector.optOut(sautr).futureValue

      result mustBe AlreadyOptedOut
    }

    "return false if NOT_FOUND" in new TestCase {
      stubOptOut(sautr, Status.NOT_FOUND, "")

      val result = entityResolverConnector.optOut(sautr).futureValue
      result mustBe PreferenceNotFound
    }

    "return false if PRECONDITION_FAILED" in new TestCase {
      stubOptOut(sautr, Status.PRECONDITION_FAILED, "")
      val result = entityResolverConnector.optOut(sautr).futureValue

      result mustBe PreferenceNotFound
    }

    "return a failed future for an unhandles status code" in new TestCase {
      stubOptOut(sautr, Status.INTERNAL_SERVER_ERROR, "")

      val result = entityResolverConnector.optOut(sautr).failed.futureValue
      result mustBe an[UnexpectedOptOutResponse]
    }

  }

}
