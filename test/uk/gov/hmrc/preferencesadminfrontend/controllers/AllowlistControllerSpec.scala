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
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Configuration
import play.api.http.Status
import play.api.i18n.MessagesApi
import play.api.libs.json.Json
import play.api.mvc.MessagesControllerComponents
import play.api.test.CSRFTokenHelper.*
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import play.mvc.Http
import uk.gov.hmrc.http.{ HeaderCarrier, HttpResponse }
import uk.gov.hmrc.play.bootstrap.config.ControllerConfig
import uk.gov.hmrc.preferencesadminfrontend.config.AppConfig
import uk.gov.hmrc.preferencesadminfrontend.connectors.MessageConnector
import uk.gov.hmrc.preferencesadminfrontend.controllers.model.User
import uk.gov.hmrc.preferencesadminfrontend.model.AllowlistEntry
import uk.gov.hmrc.preferencesadminfrontend.services.LoginService
import uk.gov.hmrc.preferencesadminfrontend.utils.SpecBase
import uk.gov.hmrc.preferencesadminfrontend.views.html.{ ErrorTemplate, allowlist_add, allowlist_delete, allowlist_show }

import scala.concurrent.{ ExecutionContext, Future }

class AllowlistControllerSpec extends PlaySpec with GuiceOneAppPerSuite with SpecBase {
  ControllerConfig.fromConfig(Configuration())
  val injector = app.injector

  implicit lazy val materializer: Materializer = app.materializer

  implicit val messagesApi: MessagesApi = app.injector.instanceOf[MessagesApi]

  app.injector.instanceOf[Configuration] must not be null

  "showAllowlistPage" should {

    "return 200 (Ok) when a populated allowlist is successfully retrieved from the message service" in new AllowlistControllerTestCase {
      private val fakeRequestWithSession =
        FakeRequest(routes.AllowlistController.showAllowlistPage()).withSession(User.sessionKey -> "user")
      private val allowlistJson = Json.parse("""
                                               |{
                                               | "formIdList" : ["SA359 2018","SA251 2018","SA370 2018"]
                                               |}
        """.stripMargin)
      when(mockMessageConnector.getAllowlist()(any[HeaderCarrier])).thenReturn(
        Future.successful(
          HttpResponse(Http.Status.OK, allowlistJson, Map.empty)
        )
      )
      private val result = allowlistController.showAllowlistPage()(fakeRequestWithSession.withCSRFToken)
      status(result) mustBe Status.OK
    }

    "return 200 (Ok) when an empty allowlist is successfully retrieved from the message service" in new AllowlistControllerTestCase {
      private val fakeRequestWithSession =
        FakeRequest(routes.AllowlistController.showAllowlistPage()).withSession(User.sessionKey -> "user")
      private val allowlistJson = Json.parse("""
                                               |{
                                               | "formIdList" : []
                                               |}
        """.stripMargin)
      when(mockMessageConnector.getAllowlist()(any[HeaderCarrier])).thenReturn(
        Future.successful(
          HttpResponse(Http.Status.OK, allowlistJson, Map.empty)
        )
      )
      private val result = allowlistController.showAllowlistPage()(fakeRequestWithSession.withCSRFToken)
      status(result) mustBe Status.OK
    }

    "return 502 (Bad Gateway) when the message service returns an invalid allowlist" in new AllowlistControllerTestCase {
      private val fakeRequestWithSession =
        FakeRequest(routes.AllowlistController.showAllowlistPage()).withSession(User.sessionKey -> "user")
      private val allowlistJson = Json.parse("""
                                               |{
                                               | "blah" : "blah"
                                               |}
        """.stripMargin)
      when(mockMessageConnector.getAllowlist()(any[HeaderCarrier])).thenReturn(
        Future.successful(
          HttpResponse(Http.Status.OK, allowlistJson, Map.empty)
        )
      )
      private val result = allowlistController.showAllowlistPage()(fakeRequestWithSession.withCSRFToken)
      status(result) mustBe Status.BAD_GATEWAY
    }

    "return 502 (Bad Gateway) when the message service returns any other status" in new AllowlistControllerTestCase {
      private val fakeRequestWithForm =
        FakeRequest(routes.AllowlistController.showAllowlistPage()).withSession(User.sessionKey -> "user")
      when(mockMessageConnector.getAllowlist()(any[HeaderCarrier])).thenReturn(
        Future.successful(
          HttpResponse(Http.Status.NOT_FOUND, "not found")
        )
      )
      private val result = allowlistController.showAllowlistPage()(fakeRequestWithForm.withCSRFToken)
      status(result) mustBe Status.BAD_GATEWAY
    }

  }

  "confirmAdd" should {

    "return 303 (Redirect) when a Form ID is successfully added via the message service" in new AllowlistControllerTestCase {
      private val fakeRequestWithSession =
        FakeRequest(routes.AllowlistController.confirmAdd())
          .withFormUrlEncodedBody(
            "formId"     -> "SA316 2015",
            "reasonText" -> "some reason (text) with special characters -@.;#:+'/"
          )
          .withSession(User.sessionKey -> "user")
      when(mockMessageConnector.addFormIdToAllowlist(any[AllowlistEntry])(any[HeaderCarrier])).thenReturn(
        Future.successful(
          HttpResponse(Http.Status.CREATED, "")
        )
      )
      private val result = allowlistController.confirmAdd.apply(fakeRequestWithSession.withCSRFToken)
      status(result) mustBe Status.SEE_OTHER
    }

    "return 502 (Bad Gateway) when the message service returns any other status" in new AllowlistControllerTestCase {
      private val fakeRequestWithSession =
        FakeRequest(routes.AllowlistController.confirmAdd())
          .withFormUrlEncodedBody("formId" -> "SA316 2015", "reasonText" -> "some reason text")
          .withSession(User.sessionKey -> "user")
      when(mockMessageConnector.addFormIdToAllowlist(any[AllowlistEntry])(any[HeaderCarrier])).thenReturn(
        Future.successful(
          HttpResponse(Http.Status.NOT_FOUND, "not found")
        )
      )
      private val result = allowlistController.confirmAdd()(fakeRequestWithSession.withCSRFToken)
      status(result) mustBe Status.BAD_GATEWAY
    }

    "return 400 (Bad Request) when the Form ID JSON is not valid" in new AllowlistControllerTestCase {
      private val fakeRequestWithSession =
        FakeRequest(routes.AllowlistController.confirmAdd())
          .withFormUrlEncodedBody("reasonText" -> "some reason text")
          .withSession(User.sessionKey -> "user")
      private val result = allowlistController.confirmAdd()(fakeRequestWithSession.withCSRFToken)
      status(result) mustBe Status.BAD_REQUEST
    }

    "return 400 (Bad Request) when invalid Form ID is added" in new AllowlistControllerTestCase {
      private val fakeRequestWithSession =
        FakeRequest(routes.AllowlistController.confirmAdd())
          .withFormUrlEncodedBody("formId" -> "P8000", "reasonText" -> "some reason text")
          .withSession(User.sessionKey -> "user")
      private val result = allowlistController.confirmAdd()(fakeRequestWithSession.withCSRFToken)
      status(result) mustBe Status.BAD_REQUEST
    }
  }

  "confirmDelete" should {

    "return 303 (Redirect) when a Form ID is successfully deleted via the message service" in new AllowlistControllerTestCase {
      private val fakeRequestWithSession =
        FakeRequest(routes.AllowlistController.confirmDelete())
          .withFormUrlEncodedBody("formId" -> "SA316 2015", "reasonText" -> "some reason text")
          .withSession(User.sessionKey -> "user")

      when(mockMessageConnector.deleteFormIdFromAllowlist(any[AllowlistEntry])(any[HeaderCarrier])).thenReturn(
        Future.successful(
          HttpResponse(Http.Status.OK, "")
        )
      )
      private val result = allowlistController.confirmDelete()(fakeRequestWithSession.withCSRFToken)
      status(result) mustBe Status.SEE_OTHER
    }

    "return 502 (Bad Gateway) when the message service returns any other status" in new AllowlistControllerTestCase {
      private val fakeRequestWithSession =
        FakeRequest(routes.AllowlistController.confirmDelete())
          .withFormUrlEncodedBody("formId" -> "SA316 2015", "reasonText" -> "some reason text")
          .withSession(User.sessionKey -> "user")
      when(mockMessageConnector.deleteFormIdFromAllowlist(any[AllowlistEntry])(any[HeaderCarrier])).thenReturn(
        Future.successful(
          HttpResponse(Http.Status.NOT_FOUND, "not found")
        )
      )
      private val result = allowlistController.confirmDelete()(fakeRequestWithSession.withCSRFToken)
      status(result) mustBe Status.BAD_GATEWAY
    }

    "return 400 (Bad Request) when the form ID JSON is not valid" in new AllowlistControllerTestCase {
      private val fakeRequestWithSession =
        FakeRequest(routes.AllowlistController.confirmDelete())
          .withFormUrlEncodedBody("reasonText" -> "some reason text")
          .withSession(User.sessionKey -> "user")
      private val result = allowlistController.confirmDelete()(fakeRequestWithSession.withCSRFToken)
      status(result) mustBe Status.BAD_REQUEST
    }
  }

  class AllowlistControllerTestCase extends SpecBase {
    implicit val ecc: ExecutionContext = stubbedMCC.executionContext
    implicit val appConfig: AppConfig = app.injector.instanceOf[AppConfig]
    val errorTemplateView: ErrorTemplate = app.injector.instanceOf[ErrorTemplate]
    val mockLoginService: LoginService = mock[LoginService]
    val controllerComponents: MessagesControllerComponents = app.injector.instanceOf[MessagesControllerComponents]
    val authorisedAction: AuthorisedAction = new AuthorisedAction(mockLoginService, controllerComponents)
    val allowlistAddView: allowlist_add = app.injector.instanceOf[allowlist_add]
    val allowlistShowView: allowlist_show = app.injector.instanceOf[allowlist_show]
    val allowlistDeleteView: allowlist_delete = app.injector.instanceOf[allowlist_delete]
    val mockMessageConnector: MessageConnector = mock[MessageConnector]

    when(mockLoginService.hasRequiredRole(any, any)).thenReturn(true)

    val allowlistController: AllowlistController =
      new AllowlistController(
        authorisedAction,
        mockMessageConnector,
        stubbedMCC,
        errorTemplateView,
        allowlistAddView,
        allowlistShowView,
        allowlistDeleteView
      )

  }
}
