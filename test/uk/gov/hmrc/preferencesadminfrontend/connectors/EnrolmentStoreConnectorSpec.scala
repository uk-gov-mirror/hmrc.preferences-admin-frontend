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

import cats.syntax.option.*
import com.github.tomakehurst.wiremock.client.WireMock
import play.api.http.Status
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.preferencesadminfrontend.model.UserState.Activated
import uk.gov.hmrc.preferencesadminfrontend.model.{ PrincipalUserIds, UserState }
import uk.gov.hmrc.preferencesadminfrontend.services.model.TaxIdentifier
import uk.gov.hmrc.preferencesadminfrontend.utils.ConnectorBaseSpec

class EnrolmentStoreConnectorSpec extends ConnectorBaseSpec(EnrolmentStoreConnector.configKey) {

  "getUserIds" must {

    def stubGetUserIds(id: String, statusCode: Int, response: String): Unit =
      wireMockServer.stubFor(
        WireMock
          .get(WireMock.urlPathEqualTo(s"/enrolment-store-proxy/enrolment-store/enrolments/$id/users"))
          .withQueryParam("type", WireMock.equalTo("principal"))
          .willReturn(
            WireMock
              .aResponse()
              .withStatus(statusCode)
              .withBody(response)
          )
      )

    "return right list of principal ids upon success" in new Scope {
      stubGetUserIds(s"IR-SA~UTR~${taxId.value}", Status.OK, Json.toJson(principalUserIds).toString())

      enrolmentStoreConnector
        .getUserIds(taxId)(headerCarrier)
        .futureValue mustBe Right(List(principalUserId))
    }

    "return an empty list of principal user ids when NO_CONTENT is returned" in new Scope {
      stubGetUserIds(s"IR-SA~UTR~${taxId.value}", Status.NO_CONTENT, "")

      enrolmentStoreConnector
        .getUserIds(taxId)(headerCarrier)
        .futureValue mustBe Right(List.empty)
    }

    "return an error message when neither OK or BAD_REQUEST status code is returned" in new Scope {
      stubGetUserIds(s"IR-SA~UTR~${taxId.value}", Status.BAD_REQUEST, "BAD_NEWS")

      enrolmentStoreConnector
        .getUserIds(taxId)(headerCarrier)
        .futureValue mustBe Left(s"upstream error when getting principals, ${Status.BAD_REQUEST} BAD_NEWS")
    }

    "return an error when INTERNAL_SERVER_ERROR status code is returned" in new Scope {
      stubGetUserIds(s"IR-SA~UTR~${taxId.value}", Status.INTERNAL_SERVER_ERROR, "SERVER_ERROR")

      enrolmentStoreConnector
        .getUserIds(taxId)(headerCarrier)
        .futureValue mustBe Left(
        s"upstream error when getting principals, ${Status.INTERNAL_SERVER_ERROR} SERVER_ERROR"
      )
    }
  }

  "getUserState" must {
    def stubGetUserState(principalUserId: String, taxId: String, statusCode: Int, response: String): Unit =
      wireMockServer.stubFor(
        WireMock
          .get(WireMock.urlEqualTo(s"/enrolment-store-proxy/enrolment-store/users/$principalUserId/enrolments/$taxId"))
          .willReturn(
            WireMock
              .aResponse()
              .withStatus(statusCode)
              .withBody(response)
          )
      )

    "return right some users state upon success" in new Scope {
      stubGetUserState(principalUserId, s"IR-SA~UTR~${taxId.value}", Status.OK, Json.toJson(userState).toString())

      enrolmentStoreConnector
        .getUserState(principalUserId, taxId)(headerCarrier)
        .futureValue mustBe Right(userState.some)
    }

    "return right none when a NOT_FOUND status is returned" in new Scope {
      stubGetUserState(principalUserId, s"IR-SA~UTR~${taxId.value}", Status.NOT_FOUND, "")

      enrolmentStoreConnector
        .getUserState(principalUserId, taxId)(headerCarrier)
        .futureValue mustBe Right(None)
    }

    "return an upstream error when neither OK or NOT_FOUND status code is returned" in new Scope {
      stubGetUserState(principalUserId, s"IR-SA~UTR~${taxId.value}", Status.BAD_REQUEST, "BAD_NEWS")

      enrolmentStoreConnector
        .getUserState(principalUserId, taxId)(headerCarrier)
        .futureValue mustBe Left(s"upstream error when checking enrolment state, ${Status.BAD_REQUEST} BAD_NEWS")
    }

    "return an error when INTERNAL_SERVER_ERROR status code is returned" in new Scope {
      stubGetUserState(principalUserId, s"IR-SA~UTR~${taxId.value}", Status.INTERNAL_SERVER_ERROR, "SERVER_ERROR")

      enrolmentStoreConnector
        .getUserState(principalUserId, taxId)(headerCarrier)
        .futureValue mustBe Left(
        s"upstream error when checking enrolment state, ${Status.INTERNAL_SERVER_ERROR} SERVER_ERROR"
      )
    }
  }

  trait Scope {
    val saUtr = "MY-UTR"
    val taxId: TaxIdentifier = TaxIdentifier("sautr", saUtr)

    val principalUserId: String = "6696231619140440"
    val principalUserIds: PrincipalUserIds = Json
      .parse("""{
               |  "principalUserIds": [
               |      "6696231619140440"
               |  ]
               |}""".stripMargin)
      .as[PrincipalUserIds]
    val userState: UserState = UserState(Activated)
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

    val enrolmentStoreConnector: EnrolmentStoreConnector =
      app.injector.instanceOf[EnrolmentStoreConnector]
  }

}
