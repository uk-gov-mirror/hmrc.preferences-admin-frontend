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

import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.mvc.{ AnyContent, AnyContentAsEmpty, Request, Result }
import play.api.test.FakeRequest
import uk.gov.hmrc.preferencesadminfrontend.controllers.Role.{ Admin, Generic, SolsGeneric }
import uk.gov.hmrc.preferencesadminfrontend.controllers.model.User
import uk.gov.hmrc.preferencesadminfrontend.services.LoginService
import play.api.mvc.Results.Ok
import play.api.mvc.*
import play.api.test.Helpers.*
import org.mockito.Mockito.when
import org.mockito.ArgumentMatchers.any

import scala.concurrent.Future

class AuthorisedActionSpec extends PlaySpec with MockitoSugar with ScalaFutures with GuiceOneAppPerSuite {

  "async(role: Role)(block: Request[AnyContent] => User => Future[Result])" should {

    val authorisedAction = app.injector.instanceOf[AuthorisedAction]
    val empty_string = ""
    val blockInput: Request[AnyContent] => User => Future[Result] = _ => _ => Future.successful(Ok(empty_string))

    "return the correct page when the user has admin role" in {
      implicit val req: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
        .withSession((User.sessionKey, "admin"), ("isSols", "false"), ("isAdmin", "true"), ("isGeneric", "false"))

      val result = authorisedAction.async(Admin)(blockInput)

      val finalResult = await(result.apply(req))
      finalResult.header.status mustBe OK
    }

    "return to login page when the user has both sols and genric roles" in {
      implicit val req: FakeRequest[AnyContentAsEmpty.type] =
        FakeRequest("GET", "/paperless/admin/csv-upload-bulk-opt-outs")
          .withSession(
            (User.sessionKey, "solsUser"),
            ("isSols", "true"),
            ("isAdmin", "false"),
            ("isGeneric", "true")
          )

      val mockLoginService = mock[LoginService]

      when(mockLoginService.hasRequiredRole(any, any)).thenReturn(true)
      val result = authorisedAction.async(SolsGeneric)(blockInput)

      val finalResult = await(result.apply(req))
      finalResult.header.status mustBe SEE_OTHER
    }

    "return the correct page when the user has correct (generic) role to access CsvUpload bulk opt-outs" in {
      implicit val req: FakeRequest[AnyContentAsEmpty.type] =
        FakeRequest("GET", "/paperless/admin/csv-upload-bulk-opt-outs")
          .withSession(
            (User.sessionKey, "genericUser"),
            ("isSols", "false"),
            ("isAdmin", "false"),
            ("isGeneric", "true")
          )

      val result = authorisedAction.async(Generic)(blockInput)

      val finalResult = await(result.apply(req))
      finalResult.header.status mustBe OK
    }
  }
}
