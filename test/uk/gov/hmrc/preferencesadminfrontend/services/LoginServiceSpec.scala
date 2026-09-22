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

package uk.gov.hmrc.preferencesadminfrontend.services

import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.PlaySpec
import play.api.Configuration
import uk.gov.hmrc.preferencesadminfrontend.controllers.model.User

class LoginServiceSpec extends PlaySpec {

  "login" should {
    "allow an authorised user into the system" in new TestCase {
      lazy val loginService = new LoginService(loginServiceConfiguration)
      val user = new User("username", "password")

      loginService.isAuthorised(user) mustBe true
    }

    "not allow an user which is not included" in new TestCase {
      lazy val loginService = new LoginService(loginServiceConfiguration)
      val user = new User("anotherUser", "password")

      loginService.isAuthorised(user) mustBe false
    }

    "not allow if the password is wrong" in new TestCase {
      lazy val loginService = new LoginService(loginServiceConfiguration)
      val user = new User("username", "wrongPassword")

      loginService.isAuthorised(user) mustBe false
    }
  }

  trait TestCase {

    val loginServiceConfiguration = new LoginServiceConfiguration(mock[Configuration]) {
      override lazy val authorisedUsers: Seq[User] = Seq(User("username", "password"))
    }
  }
}
