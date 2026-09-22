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
import org.mockito.Mockito.*
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.*
import play.api.i18n.MessagesApi
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.CSRFTokenHelper.*
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditResult
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.preferencesadminfrontend.utils.SpecBase

import scala.concurrent.{ ExecutionContext, Future }

class LoginControllerSpec extends PlaySpec with GuiceOneAppPerSuite with SpecBase with ScalaFutures {

  implicit lazy val materializer: Materializer = app.materializer
  override implicit lazy val app: Application = GuiceApplicationBuilder().build()
  implicit val messagesApi: MessagesApi = app.injector.instanceOf[MessagesApi]

  "GET /" should {
    "return 200" in new MessageBrakeControllerTestCase {
      val result = loginController.showLoginPage()(FakeRequest("GET", "/").withCSRFToken)
      status(result) mustBe Status.OK
    }

    "return HTML" in new MessageBrakeControllerTestCase {
      val result = loginController.showLoginPage()(FakeRequest("GET", "/").withCSRFToken)
      contentType(result) mustBe Some("text/html")
      charset(result) mustBe Some("utf-8")
    }
  }

  "POST to login" should {
    "Redirect to the next page if credentials are correct" in new MessageBrakeControllerTestCase {
      val fakeRequest =
        FakeRequest(routes.LoginController.loginAction())
          .withFormUrlEncodedBody("username" -> "user", "password" -> "pwd")
          .withCSRFToken
      val result = loginController.loginAction()(fakeRequest)

      status(result) mustBe Status.SEE_OTHER
      headers(result) must contain("Location" -> "/paperless/admin/home")
      session(result).data must contain("userId" -> "user")
      session(result).data must contain("isAdmin" -> "false")
      session(result).data must contain("isSols" -> "false")
    }

    "Set the session param for Admin user role" in new MessageBrakeControllerTestCase {
      val fakeRequest =
        FakeRequest(routes.LoginController.loginAction())
          .withFormUrlEncodedBody("username" -> "admin", "password" -> "pwd")
          .withCSRFToken
      val result = loginController.loginAction()(fakeRequest)

      status(result) mustBe Status.SEE_OTHER
      headers(result) must contain("Location" -> "/paperless/admin/home")
      session(result).data must contain("userId" -> "admin")
      session(result).data must contain("isAdmin" -> "true")
      session(result).data must contain("isSols" -> "false")
    }

    "Set the session param for Sols user role" in new MessageBrakeControllerTestCase {
      val fakeRequest =
        FakeRequest(routes.LoginController.loginAction())
          .withFormUrlEncodedBody("username" -> "solsUser", "password" -> "pwd")
          .withCSRFToken
      val result = loginController.loginAction()(fakeRequest)

      status(result) mustBe Status.SEE_OTHER
      headers(result) must contain("Location" -> "/paperless/admin/home")
      session(result).data must contain("userId" -> "solsUser")
      session(result).data must contain("isAdmin" -> "false")
      session(result).data must contain("isSols" -> "true")
    }

    "Set the session param for generic user role" in new MessageBrakeControllerTestCase {
      val fakeRequest =
        FakeRequest(routes.LoginController.loginAction())
          .withFormUrlEncodedBody("username" -> "solsUser", "password" -> "pwd")
          .withCSRFToken

      val result = loginController.loginAction()(fakeRequest)

      status(result) mustBe Status.SEE_OTHER
      headers(result) must contain("Location" -> "/paperless/admin/home")
      session(result).data must contain("userId" -> "solsUser")
      session(result).data must contain("isAdmin" -> "false")
      session(result).data must contain("isSols" -> "true")
      session(result).data must contain("isGeneric" -> "true")
    }

    "Return unauthorised if credentials are not correct" in new MessageBrakeControllerTestCase {
      val fakeRequest =
        FakeRequest(routes.LoginController.loginAction())
          .withFormUrlEncodedBody("username" -> "user", "password" -> "wrongPassword")
          .withCSRFToken
      val result = loginController.loginAction()(fakeRequest)

      status(result) mustBe Status.UNAUTHORIZED
    }

    "Return bad request if credentials are missing" in new MessageBrakeControllerTestCase {
      val fakeRequest =
        FakeRequest(routes.LoginController.loginAction())
          .withFormUrlEncodedBody()
          .withCSRFToken
      val result = loginController.loginAction()(fakeRequest)

      status(result) mustBe Status.BAD_REQUEST
    }
  }

  "POST to logout" should {
    "Destroy existing session and redirect to login page" in new MessageBrakeControllerTestCase {
      val result = loginController.logoutAction()(FakeRequest().withSession("userId" -> "user").withCSRFToken)

      session(result).data must not contain ("userId" -> "user")
      status(result) mustBe Status.SEE_OTHER
      headers(result) must contain("Location" -> "/paperless/admin")
    }
  }

  class MessageBrakeControllerTestCase extends SpecBase {
    when(auditConnectorMock.sendEvent(any[DataEvent])(any[HeaderCarrier], any[ExecutionContext]))
      .thenReturn(Future.successful(AuditResult.Success))
    val loginController: LoginController = app.injector.instanceOf[LoginController]
  }
}
