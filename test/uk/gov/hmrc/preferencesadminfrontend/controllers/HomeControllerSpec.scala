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
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.*
import play.api.i18n.MessagesApi
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.preferencesadminfrontend.controllers.model.User
import uk.gov.hmrc.preferencesadminfrontend.utils.SpecBase

class HomeControllerSpec extends PlaySpec with GuiceOneAppPerSuite with SpecBase with ScalaFutures {

  implicit lazy val materializer: Materializer = app.materializer
  override implicit lazy val app: Application = GuiceApplicationBuilder().build()
  implicit val messagesApi: MessagesApi = app.injector.instanceOf[MessagesApi]
  val homeController: HomeController = app.injector.instanceOf[HomeController]

  "GET /home" should {
    "return 200" in {
      val result = homeController.showHomePage()(FakeRequest("GET", "/home").withSession(User.sessionKey -> "user"))
      status(result) mustBe Status.OK
    }

    "return HTML" in {
      val result = homeController.showHomePage()(FakeRequest("GET", "/home").withSession(User.sessionKey -> "user"))
      contentType(result) mustBe Some("text/html")
      charset(result) mustBe Some("utf-8")
    }
  }
}
