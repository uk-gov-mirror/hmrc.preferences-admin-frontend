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
import com.github.tomakehurst.wiremock.http.Fault
import org.scalactic.source.Position
import play.api.http.Status
import play.api.libs.json.{ JsSuccess, Json }
import play.shaded.ahc.org.asynchttpclient.exception.RemotelyClosedException
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.preferencesadminfrontend.connectors.ChannelPreferencesConnector.StatusUpdate
import uk.gov.hmrc.preferencesadminfrontend.services.model.csv.CsvData
import uk.gov.hmrc.preferencesadminfrontend.utils.ConnectorBaseSpec

import scala.concurrent.Future

class ChannelPreferencesConnectorSpec extends ConnectorBaseSpec(ChannelPreferencesConnector.configKey) {

  def testServerFailureCall(call: => Future[Either[String, Unit]])(implicit position: Position): Unit = {
    wireMockServer.stubFor(
      WireMock
        .any(WireMock.anyUrl())
        .willReturn(WireMock.aResponse().withFault(Fault.RANDOM_DATA_THEN_CLOSE))
    )

    val result: Throwable = call.failed.futureValue
    result mustBe a[RemotelyClosedException]
  }

  "updateStatus" must {
    def stubUpdateStatus(statusUpdate: StatusUpdate, statusCode: Int, responseText: String): Unit =
      wireMockServer.stubFor(
        WireMock
          .post(WireMock.urlEqualTo(s"/channel-preferences/preference/itsa/status"))
          .withRequestBody(WireMock.equalToJson(Json.toJson(statusUpdate).toString))
          .willReturn(
            WireMock
              .aResponse()
              .withStatus(statusCode)
              .withBody(responseText)
          )
      )
    val statusUpdate: StatusUpdate = StatusUpdate("ITSA-NICE-DAY", true)

    "return right SentStatus.Sent upon success" in new TestCase {
      stubUpdateStatus(statusUpdate, Status.OK, "")

      channelPreferencesConnector
        .updateStatus(statusUpdate)(headerCarrier)
        .futureValue mustBe Right(())
    }

    "return left upstream error message upon a failure response" in new TestCase {
      stubUpdateStatus(statusUpdate, Status.BAD_REQUEST, "bad update status request")

      channelPreferencesConnector
        .updateStatus(statusUpdate)(headerCarrier)
        .futureValue mustBe Left(
        s"upstream error when sending status update, ${Status.BAD_REQUEST} bad update status request"
      )
    }

    "return a failed future on server problems" in new TestCase {
      testServerFailureCall(
        channelPreferencesConnector
          .updateStatus(statusUpdate)(headerCarrier)
      )
    }
  }

  "process" must {
    val csvData: CsvData = CsvData("1", "2", "3")

    def stubProcess(csvData: CsvData, statusCode: Int, responseText: String): Unit =
      wireMockServer.stubFor(
        WireMock
          .post(WireMock.urlEqualTo(s"/channel-preferences/process"))
          .withRequestBody(WireMock.equalToJson(Json.toJson(csvData).toString))
          .willReturn(
            WireMock
              .aResponse()
              .withStatus(statusCode)
              .withBody(responseText)
          )
      )

    "return right status upon success" in new TestCase {
      stubProcess(csvData, Status.OK, "")

      channelPreferencesConnector
        .process(csvData)(headerCarrier)
        .futureValue mustBe Right(())
    }

    "return left upstream error message upon a failure response" in new TestCase {
      stubProcess(csvData, Status.BAD_REQUEST, "bad process request")

      channelPreferencesConnector
        .process(csvData)(headerCarrier)
        .futureValue mustBe Left(s"upstream error when sending the request, ${Status.BAD_REQUEST} bad process request")
    }

    "return a failed future on server problems" in new TestCase {
      testServerFailureCall(
        channelPreferencesConnector
          .process(csvData)(headerCarrier)
      )
    }

  }

  trait TestCase {
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()
    val channelPreferencesConnector: ChannelPreferencesConnector =
      app.injector.instanceOf[ChannelPreferencesConnector]
  }

  "StatusUpdate" must {

    "deserialize from JSON" in {
      val json = Json.parse(
        """{
          | "enrolment": "X123456789",
          | "status": true
          |}""".stripMargin
      )

      val result = json.validate[StatusUpdate]

      result mustBe JsSuccess(StatusUpdate("X123456789", status = true))
    }

    "serialize to JSON" in {
      val statusUpdate = StatusUpdate("X123456789", status = false)

      val result = Json.toJson(statusUpdate)

      (result \ "enrolment").as[String] mustBe "X123456789"
      (result \ "status").as[Boolean] mustBe false
    }
  }

}
