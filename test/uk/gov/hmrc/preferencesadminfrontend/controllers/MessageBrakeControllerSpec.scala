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

package uk.gov.hmrc.preferencesadminfrontend.controllers

import org.apache.pekko.stream.Materializer
import org.jsoup.Jsoup
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status
import play.api.i18n.MessagesApi
import play.api.mvc.{ AnyContentAsEmpty, AnyContentAsFormUrlEncoded, Call, MessagesControllerComponents }
import play.api.test.CSRFTokenHelper.*
import play.api.test.FakeRequest
import play.api.test.Helpers.{ contentAsString, defaultAwaitTimeout, status }
import uk.gov.hmrc.http.{ HeaderCarrier, HttpResponse }
import uk.gov.hmrc.preferencesadminfrontend.config.AppConfig
import uk.gov.hmrc.preferencesadminfrontend.controllers.model.User
import uk.gov.hmrc.preferencesadminfrontend.model.{ BatchMessagePreview, GmcBatch, GmcBatchApproval, MessagePreview }
import uk.gov.hmrc.preferencesadminfrontend.services.{ LoginService, MessageService }
import uk.gov.hmrc.preferencesadminfrontend.utils.SpecBase
import uk.gov.hmrc.preferencesadminfrontend.views.html.{ ErrorTemplate, batch_approval, batch_rejection, message_brake_admin }

import scala.concurrent.{ ExecutionContext, Future }

class MessageBrakeControllerSpec extends PlaySpec with GuiceOneAppPerSuite with SpecBase with ScalaFutures {

  implicit lazy val materializer: Materializer = app.materializer

  implicit val messagesApi: MessagesApi = app.injector.instanceOf[MessagesApi]

  implicit lazy val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

  implicit lazy val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

  implicit val hc: HeaderCarrier = mock[HeaderCarrier]

  val gmcBatch = GmcBatch(
    "123456789",
    "SA359",
    "2017-03-16",
    "newMessageAlert_SA359",
    Some(15778)
  )

  val gmcBatchApproval = GmcBatchApproval(
    "123456789",
    "SA359",
    "2017-03-16",
    "newMessageAlert_SA359",
    "some reason"
  )

  val mockMessagePreview = MessagePreview(
    "subject",
    Some(""),
    "content",
    Some(""),
    "123456789",
    "messageType",
    "03/04/1995",
    "AB123456C"
  )

  val mockedBatchMessagePreview = BatchMessagePreview(
    mockMessagePreview,
    "123456789"
  )

  "showAdminPage" should {

    "return a 200 when the admin page is successfully populated with Gmc Message Batches" in new MessageBrakeControllerTestCase {
      private val fakeRequestWithSession =
        FakeRequest(routes.MessageBrakeController.showAdminPage()).withSession(User.sessionKey -> "user")
      when(messageServiceMock.getGmcBatches()(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(Left(Seq(gmcBatch))))
      private val result = messageBrakeController().showAdminPage()(fakeRequestWithSession.withCSRFToken)
      status(result) mustBe Status.OK
    }

    "return error" in new MessageBrakeControllerTestCase {
      private val fakeRequestWithSession =
        FakeRequest(routes.MessageBrakeController.showAdminPage()).withSession(User.sessionKey -> "user")
      when(messageServiceMock.getGmcBatches()(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(Right("error")))
      private val result = messageBrakeController().showAdminPage()(fakeRequestWithSession.withCSRFToken)
      status(result) mustBe Status.BAD_GATEWAY
    }
  }

  "previewMessage" should {

    "return 200 when the preview page has been populated with a single message" in new MessageBrakeControllerTestCase {
      val requestWithFormData = getRequestWithFormData(routes.MessageBrakeController.previewMessage())
      when(messageServiceMock.getGmcBatches()(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(Left(Seq(gmcBatch))))
      when(
        messageServiceMock
          .getRandomMessagePreview(ArgumentMatchers.eq(gmcBatch))(any[HeaderCarrier], any[ExecutionContext])
      )
        .thenReturn(Future(Left(mockedBatchMessagePreview)))
      private val result = messageBrakeController().previewMessage()(requestWithFormData.withCSRFToken)
      status(result) mustBe Status.OK
    }

    "return error when getGmcBatches() fails" in new MessageBrakeControllerTestCase {
      val requestWithFormData = getRequestWithFormData(routes.MessageBrakeController.previewMessage())
      when(messageServiceMock.getGmcBatches()(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(Right("error")))
      when(
        messageServiceMock
          .getRandomMessagePreview(ArgumentMatchers.eq(gmcBatch))(any[HeaderCarrier], any[ExecutionContext])
      )
        .thenReturn(Future(Left(mockedBatchMessagePreview)))
      private val result = messageBrakeController().previewMessage()(requestWithFormData.withCSRFToken)
      status(result) mustBe Status.BAD_GATEWAY
    }

    "return error when getRandomMessagePreview() fails" in new MessageBrakeControllerTestCase {
      val requestWithFormData = getRequestWithFormData(routes.MessageBrakeController.previewMessage())
      when(messageServiceMock.getGmcBatches()(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(Left(Seq(gmcBatch))))
      when(
        messageServiceMock
          .getRandomMessagePreview(ArgumentMatchers.eq(gmcBatch))(any[HeaderCarrier], any[ExecutionContext])
      )
        .thenReturn(Future(Right("error")))
      private val result = messageBrakeController().previewMessage()(requestWithFormData.withCSRFToken)
      status(result) mustBe Status.BAD_GATEWAY
    }
  }

  "showApproveBatchConfirmationPage" should {
    "return 200 with the approved batchIds & confirmation text" in new MessageBrakeControllerTestCase {
      val requestWithFormData = getRequestWithFormData(routes.MessageBrakeController.showApproveBatchConfirmationPage())

      private val result =
        messageBrakeController().showApproveBatchConfirmationPage()(requestWithFormData.withCSRFToken)
      status(result) mustBe Status.OK
      Jsoup
        .parse(contentAsString(result))
        .getElementsByClass("govuk-body")
        .first()
        .text() mustBe "Please see you are approving the messages for following form id:"
    }
  }

  "showRejectBatchConfirmationPage" should {
    "return 200 with the approved batchIds & confirmation text" in new MessageBrakeControllerTestCase {
      val requestWithFormData = getRequestWithFormData(routes.MessageBrakeController.showRejectBatchConfirmationPage())

      private val result = messageBrakeController().showRejectBatchConfirmationPage()(requestWithFormData.withCSRFToken)
      status(result) mustBe Status.OK
      Jsoup
        .parse(contentAsString(result))
        .getElementsByClass("govuk-body")
        .first()
        .text() mustBe "Please see you are rejecting the messages for following form id:"
    }
  }

  "confirmApproveBatch" should {

    "return 200 when the approve batch page is posted" in new MessageBrakeControllerTestCase {
      val requestWithFormData = getRequestWithFormData(routes.MessageBrakeController.confirmApproveBatch())
      when(messageConnectorMock.approveGmcBatch(ArgumentMatchers.eq(gmcBatchApproval))(any[HeaderCarrier]))
        .thenReturn(Future(HttpResponse(Status.OK, "")))
      when(messageServiceMock.getGmcBatches()(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(Left(Seq(gmcBatch))))
      private val result = messageBrakeController().confirmApproveBatch()(requestWithFormData.withCSRFToken)
      status(result) mustBe Status.OK
    }

    "return a BAD REQUEST (404) when the form payload is invalid" in new MessageBrakeControllerTestCase {
      val fakeRequestWithSession =
        FakeRequest(routes.MessageBrakeController.confirmApproveBatch()).withSession(User.sessionKey -> "user")
      val requestWithFormData = fakeRequestWithSession.withFormUrlEncodedBody("blah" -> "blah")
      when(messageConnectorMock.approveGmcBatch(ArgumentMatchers.eq(gmcBatchApproval))(any[HeaderCarrier]))
        .thenReturn(Future(HttpResponse(Status.BAD_GATEWAY, "")))
      when(messageServiceMock.getGmcBatches()(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(Left(Seq(gmcBatch))))
      private val result = messageBrakeController().confirmApproveBatch()(requestWithFormData.withCSRFToken)
      status(result) mustBe Status.BAD_REQUEST
    }

    "return error when confirmApproveBatch() fails" in new MessageBrakeControllerTestCase {
      val requestWithFormData = getRequestWithFormData(routes.MessageBrakeController.confirmApproveBatch())
      when(messageConnectorMock.approveGmcBatch(ArgumentMatchers.eq(gmcBatchApproval))(any[HeaderCarrier]))
        .thenReturn(Future(HttpResponse(Status.BAD_GATEWAY, "")))
      when(messageServiceMock.getGmcBatches()(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(Left(Seq(gmcBatch))))
      private val result = messageBrakeController().confirmApproveBatch()(requestWithFormData.withCSRFToken)
      status(result) mustBe Status.BAD_GATEWAY
    }

    "return error when getGmcBatches() fails" in new MessageBrakeControllerTestCase {
      val requestWithFormData = getRequestWithFormData(routes.MessageBrakeController.confirmApproveBatch())
      when(messageConnectorMock.approveGmcBatch(ArgumentMatchers.eq(gmcBatchApproval))(any[HeaderCarrier]))
        .thenReturn(Future(HttpResponse(Status.OK, "")))
      when(messageServiceMock.getGmcBatches()(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(Right("error")))
      private val result = messageBrakeController().confirmApproveBatch()(requestWithFormData.withCSRFToken)
      status(result) mustBe Status.BAD_GATEWAY
    }
  }

  "confirmRejectBatch" should {

    "return 200 when the approve batch page is posted" in new MessageBrakeControllerTestCase {
      val requestWithFormData = getRequestWithFormData(routes.MessageBrakeController.confirmRejectBatch())
      when(messageConnectorMock.rejectGmcBatch(ArgumentMatchers.eq(gmcBatchApproval))(any[HeaderCarrier]))
        .thenReturn(Future(HttpResponse(Status.OK, "")))
      when(messageServiceMock.getGmcBatches()(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(Left(Seq(gmcBatch))))
      private val result = messageBrakeController().confirmRejectBatch()(requestWithFormData.withCSRFToken)
      status(result) mustBe Status.OK
    }

    "return a BAD REQUEST (404) when the form payload is invalid" in new MessageBrakeControllerTestCase {
      val fakeRequestWithSession =
        FakeRequest(routes.MessageBrakeController.confirmRejectBatch()).withSession(User.sessionKey -> "user")
      val requestWithFormData = fakeRequestWithSession.withFormUrlEncodedBody("blah" -> "blah")
      when(messageConnectorMock.approveGmcBatch(ArgumentMatchers.eq(gmcBatchApproval))(any[HeaderCarrier]))
        .thenReturn(Future(HttpResponse(Status.BAD_GATEWAY, "")))
      when(messageServiceMock.getGmcBatches()(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(Left(Seq(gmcBatch))))
      private val result = messageBrakeController().confirmRejectBatch()(requestWithFormData.withCSRFToken)
      status(result) mustBe Status.BAD_REQUEST
    }

    "return error when rejectGmcBatch() fails" in new MessageBrakeControllerTestCase {
      val requestWithFormData = getRequestWithFormData(routes.MessageBrakeController.confirmRejectBatch())
      when(messageConnectorMock.rejectGmcBatch(ArgumentMatchers.eq(gmcBatchApproval))(any[HeaderCarrier]))
        .thenReturn(Future(HttpResponse(Status.BAD_GATEWAY, "")))
      when(messageServiceMock.getGmcBatches()(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(Left(Seq(gmcBatch))))
      private val result = messageBrakeController().confirmRejectBatch()(requestWithFormData.withCSRFToken)
      status(result) mustBe Status.BAD_GATEWAY
    }

    "return error when getGmcBatches() fails" in new MessageBrakeControllerTestCase {
      val requestWithFormData = getRequestWithFormData(routes.MessageBrakeController.confirmRejectBatch())
      when(messageConnectorMock.rejectGmcBatch(ArgumentMatchers.eq(gmcBatchApproval))(any[HeaderCarrier]))
        .thenReturn(Future(HttpResponse(Status.OK, "")))
      when(messageServiceMock.getGmcBatches()(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(Right("error")))
      private val result = messageBrakeController().confirmRejectBatch()(requestWithFormData.withCSRFToken)
      status(result) mustBe Status.BAD_GATEWAY
    }
  }

  class MessageBrakeControllerTestCase extends SpecBase {

    implicit val ecc: ExecutionContext = stubbedMCC.executionContext

    val errorTemplateView: ErrorTemplate = app.injector.instanceOf[ErrorTemplate]
    val mockLoginService: LoginService = mock[LoginService]
    val controllerComponents: MessagesControllerComponents = app.injector.instanceOf[MessagesControllerComponents]
    val authorisedAction: AuthorisedAction = new AuthorisedAction(mockLoginService, controllerComponents)
    val batchApprovalView: batch_approval = app.injector.instanceOf[batch_approval]
    val batchRejectionView: batch_rejection = app.injector.instanceOf[batch_rejection]
    val messageBrakeAdminView: message_brake_admin = app.injector.instanceOf[message_brake_admin]

    val messageServiceMock: MessageService = mock[MessageService]

    when(mockLoginService.hasRequiredRole(any, any)).thenReturn(true)
    def messageBrakeController()(implicit appConfig: AppConfig): MessageBrakeController =
      new MessageBrakeController(
        authorisedAction,
        messageConnectorMock,
        messageServiceMock,
        stubbedMCC,
        errorTemplateView,
        batchApprovalView,
        batchRejectionView,
        messageBrakeAdminView
      )

    def getRequestWithFormData(routeCall: Call): FakeRequest[AnyContentAsFormUrlEncoded] = {
      val fakeRequestWithSession = FakeRequest(routeCall).withSession(User.sessionKey -> "user")
      fakeRequestWithSession.withFormUrlEncodedBody(
        "batchId"    -> "123456789",
        "formId"     -> "SA359",
        "issueDate"  -> "2017-03-16",
        "templateId" -> "newMessageAlert_SA359",
        "count"      -> "15778",
        "reasonText" -> "some reason"
      )
    }
  }
}
