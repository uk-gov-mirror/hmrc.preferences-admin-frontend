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
import play.api.libs.json.*
import uk.gov.hmrc.http.{ HeaderCarrier, HttpResponse }
import uk.gov.hmrc.preferencesadminfrontend.model.*
import uk.gov.hmrc.preferencesadminfrontend.utils.ConnectorBaseSpec

import scala.concurrent.Future

class MessageConnectorSpec extends ConnectorBaseSpec(MessageConnector.configKey) {

  "GMC Batches Admin" should {
    def testServerFailureCall(call: => Future[HttpResponse])(implicit position: Position): Unit = {
      wireMockServer.stubFor(
        WireMock
          .any(WireMock.anyUrl())
          .willReturn(WireMock.aResponse().withFault(Fault.RANDOM_DATA_THEN_CLOSE))
      )

      val result = call.futureValue
      result.status mustBe Status.BAD_GATEWAY
      result.body mustBe "Remotely closed"
    }

    def testNonSuccessStatusCode(call: => Future[HttpResponse])(implicit position: Position): Unit = {
      wireMockServer.stubFor(
        WireMock
          .any(WireMock.anyUrl())
          .willReturn(
            WireMock
              .aResponse()
              .withStatus(Status.INTERNAL_SERVER_ERROR)
              .withBody("Unexpected error from server")
          )
      )

      val result = call.futureValue
      result.status mustBe Status.INTERNAL_SERVER_ERROR
      result.body mustBe "Unexpected error from server"

    }

    "getGmcBatches - v4 message" should {

      "return a valid sequence of batches with status 200" in new TestCase {
        wireMockServer.stubFor(
          WireMock
            .get(WireMock.urlPathEqualTo(s"/secure-messaging/admin/message/brake/gmc/batches"))
            .willReturn(
              WireMock
                .aResponse()
                .withStatus(Status.OK)
                .withBody("get gmc batches response")
            )
        )

        val result: HttpResponse = messageConnector.getGmcBatches().futureValue
        result.status mustBe Status.OK
        result.body mustBe "get gmc batches response"
      }

      "return the response if it is normal error" in new TestCase {
        testNonSuccessStatusCode(messageConnector.getGmcBatches())
      }

      "return a BAD GATEWAY with an error message when an error is thrown" in new TestCase {
        testServerFailureCall(messageConnector.getGmcBatches())
      }
    }

    "getRandomMessagePreview - v4 message" should {

      val gmcBatchV4 = GmcBatch(
        "123456789",
        "SA359",
        "2017-03-16",
        "newMessageAlert_SA359",
        Some(15778)
      )

      "return a valid sequence of batches with status 200" in new TestCase {

        wireMockServer.stubFor(
          WireMock
            .post(WireMock.urlPathEqualTo(s"/secure-messaging/admin/message/brake/random"))
            .withRequestBody(WireMock.equalToJson(Json.toJson(gmcBatchV4).toString))
            .willReturn(
              WireMock
                .aResponse()
                .withStatus(Status.OK)
                .withBody("message preview response")
            )
        )

        val result = messageConnector.getRandomMessagePreview(gmcBatchV4).futureValue
        result.status mustBe Status.OK
        result.body mustBe "message preview response"
      }

      "return the response if it is normal error" in new TestCase {
        testNonSuccessStatusCode(messageConnector.getRandomMessagePreview(gmcBatchV4))
      }

      "return a BAD GATEWAY with an error message when an error is thrown" in new TestCase {
        testServerFailureCall(messageConnector.getRandomMessagePreview(gmcBatchV4))
      }
    }

    "approveGmcBatch - v4 message" should {
      "return a valid sequence of batches with status 200" in new TestCase {
        wireMockServer.stubFor(
          WireMock
            .post(WireMock.urlPathEqualTo(s"/secure-messaging/admin/message/brake/accept"))
            .withRequestBody(WireMock.equalToJson(Json.toJson(gmcBatchApprovalV4).toString))
            .willReturn(
              WireMock
                .aResponse()
                .withStatus(Status.OK)
                .withBody("approval response")
            )
        )

        val result = messageConnector.approveGmcBatch(gmcBatchApprovalV4).futureValue
        result.status mustBe Status.OK
        result.body mustBe "approval response"
      }

      "return the response if it is normal error" in new TestCase {
        testNonSuccessStatusCode(messageConnector.approveGmcBatch(gmcBatchApprovalV4))
      }

      "return a BAD GATEWAY with an error message when an error is thrown" in new TestCase {
        testServerFailureCall(messageConnector.approveGmcBatch(gmcBatchApprovalV4))
      }
    }

    "rejectGmcBatch - v4 message" should {
      "return a valid sequence of batches with status 200" in new TestCase {
        wireMockServer.stubFor(
          WireMock
            .post(WireMock.urlPathEqualTo(s"/secure-messaging/admin/message/brake/reject"))
            .withRequestBody(WireMock.equalToJson(Json.toJson(gmcBatchApprovalV4).toString))
            .willReturn(
              WireMock
                .aResponse()
                .withStatus(Status.OK)
                .withBody("approval response")
            )
        )

        val result = messageConnector.rejectGmcBatch(gmcBatchApprovalV4).futureValue
        result.status mustBe Status.OK
        result.body mustBe "approval response"
      }

      "return the response if it is normal error" in new TestCase {
        testNonSuccessStatusCode(messageConnector.rejectGmcBatch(gmcBatchApprovalV4))
      }

      "return a BAD GATEWAY with an error message when an error is thrown" in new TestCase {
        testServerFailureCall(messageConnector.rejectGmcBatch(gmcBatchApprovalV4))
      }
    }

    "Allowlist Admin" should {

      "getAllowlist" should {
        "return 200 OK with the current allowlist" in new TestCase {
          wireMockServer.stubFor(
            WireMock
              .get(WireMock.urlPathEqualTo(s"/secure-messaging/admin/message/brake/gmc/allowlist"))
              .willReturn(
                WireMock
                  .aResponse()
                  .withStatus(Status.OK)
                  .withBody("allow list")
              )
          )

          val result = messageConnector.getAllowlist().futureValue
          result.status mustBe Status.OK
          result.body mustBe "allow list"
        }

        "return the response if it is normal error" in new TestCase {
          testNonSuccessStatusCode(messageConnector.getAllowlist())
        }

        "return a BAD GATEWAY with an error message when an error is thrown" in new TestCase {
          testServerFailureCall(messageConnector.getAllowlist())
        }
      }

      "addFormIdToAllowlist" should {
        val entry = AllowlistEntry("formId", "reason")
        "return 200 OK when a form is successfully added" in new TestCase {
          wireMockServer.stubFor(
            WireMock
              .post(WireMock.urlPathEqualTo(s"/secure-messaging/admin/message/brake/gmc/allowlist/add"))
              .withRequestBody(WireMock.equalToJson(Json.toJson(entry).toString))
              .willReturn(
                WireMock
                  .aResponse()
                  .withStatus(Status.OK)
                  .withBody("allow list added")
              )
          )

          val result = messageConnector.addFormIdToAllowlist(entry).futureValue

          result.status mustBe Status.OK
          result.body mustBe "allow list added"
        }

        "return the response if it is normal error" in new TestCase {
          testNonSuccessStatusCode(messageConnector.addFormIdToAllowlist(entry))
        }

        "return a BAD GATEWAY with an error message when an error is thrown" in new TestCase {
          testServerFailureCall(messageConnector.addFormIdToAllowlist(entry))
        }
      }

      "deleteFormIdFromAllowlist" should {
        val entry = AllowlistEntry("formId", "reason")
        "return 200 OK when a form is successfully deleted" in new TestCase {

          wireMockServer.stubFor(
            WireMock
              .post(WireMock.urlPathEqualTo(s"/secure-messaging/admin/message/brake/gmc/allowlist/delete"))
              .withRequestBody(WireMock.equalToJson(Json.toJson(entry).toString))
              .willReturn(
                WireMock
                  .aResponse()
                  .withStatus(Status.OK)
                  .withBody("allow list added")
              )
          )

          val result = messageConnector.deleteFormIdFromAllowlist(entry).futureValue
          result.status mustBe Status.OK
        }

        "return the response if it is normal error" in new TestCase {
          testNonSuccessStatusCode(messageConnector.deleteFormIdFromAllowlist(entry))
        }

        "return a BAD GATEWAY with an error message when an error is thrown" in new TestCase {
          testServerFailureCall(messageConnector.deleteFormIdFromAllowlist(entry))
        }
      }
    }

    trait TestCase {
      implicit val hc: HeaderCarrier = HeaderCarrier()

      val gmcBatchApprovalV4 = GmcBatchApproval(
        "123456789",
        "SA359",
        "2017-03-16",
        "newMessageAlert_SA359",
        "some reason"
      )

      val messageConnector: MessageConnector = app.injector.instanceOf[MessageConnector]
    }
  }

}
