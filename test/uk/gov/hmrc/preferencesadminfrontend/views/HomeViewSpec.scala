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

package uk.gov.hmrc.preferencesadminfrontend.views

import org.jsoup.Jsoup
import org.jsoup.nodes.{ Document, Element }
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import uk.gov.hmrc.preferencesadminfrontend.controllers.routes
import uk.gov.hmrc.preferencesadminfrontend.utils.ViewSpecBase
import uk.gov.hmrc.preferencesadminfrontend.views.html.home
import play.api.test.FakeRequest
import uk.gov.hmrc.preferencesadminfrontend.controllers.model.User
import play.twirl.api.Html

class HomeViewSpec extends ViewSpecBase {

  "view" should {

    "display the correct contents for admin role" in {
      implicit val req: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
        .withSession((User.sessionKey, "admin"), ("isAdmin", "true"), ("isGeneric", "false"), ("isSols", "false"))

      viewAsDocument.getElementsByTag("h1").text() shouldBe "Home"
      viewAsDocument
        .getElementsByTag("p")
        .get(0)
        .text() shouldBe "You can access the following control pages by clicking on the appropriate link"

      val ulElelemt: Element = viewAsDocument.getElementsByTag("ul").get(0)
      val liElements = ulElelemt.getElementsByTag("li")

      liElements.get(0).text() shouldBe "Paperless Admin"
      liElements.get(1).text() shouldBe "Message Brake"
      liElements.get(2).text() shouldBe "Message Brake Allowlist"
      liElements.get(3).text() shouldBe "Message Decode"
      liElements.get(4).text() shouldBe "Multi Search"
      liElements.get(5).text() shouldBe "File Upload Id Updates"
      liElements.get(6).text() shouldBe "Bulk Opt Out"
    }

    "display the correct contents for generic role" in {
      implicit val req: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
        .withSession((User.sessionKey, "solsUser"), ("isAdmin", "false"), ("isGeneric", "true"), ("isSols", "false"))

      viewAsDocument.getElementsByTag("h1").text() shouldBe "Home"
      viewAsDocument
        .getElementsByTag("p")
        .get(0)
        .text() shouldBe "You can access the following control pages by clicking on the appropriate link"

      val ulElelemt: Element = viewAsDocument.getElementsByTag("ul").get(0)
      val liElements = ulElelemt.getElementsByTag("li")

      liElements.get(0).text() shouldBe "Paperless Admin"
      liElements.get(1).text() shouldBe "Bulk Opt Out"
    }

    "display the correct contents for sols role" in {
      implicit val req: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
        .withSession((User.sessionKey, "solsUser"), ("isSols", "true"), ("isAdmin", "false"), ("isGeneric", "false"))

      viewAsDocument.getElementsByTag("h1").text() shouldBe "Home"
      viewAsDocument
        .getElementsByTag("p")
        .get(0)
        .text() shouldBe "You can access the following control pages by clicking on the appropriate link"

      val ulElelemt: Element = viewAsDocument.getElementsByTag("ul").get(0)
      val liElements = ulElelemt.getElementsByTag("li")

      liElements.get(0).text() shouldBe "Paperless Admin"
    }

    "do not display the bulk opt-outs option for a user which has both sols and generic roles" in {
      implicit val req: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
        .withSession((User.sessionKey, "solsUser"), ("isSols", "true"), ("isAdmin", "false"), ("isGeneric", "true"))

      viewAsDocument.getElementsByTag("h1").text() shouldBe "Home"
      viewAsDocument
        .getElementsByTag("p")
        .get(0)
        .text() shouldBe "You can access the following control pages by clicking on the appropriate link"

      val ulElelemt: Element = viewAsDocument.getElementsByTag("ul").get(0)
      val liElements = ulElelemt.getElementsByTag("li")

      liElements.get(0).text() shouldBe "Paperless Admin"

      viewAsDocument.text() should not include "Bulk Opt Out"

      intercept[IndexOutOfBoundsException] {
        liElements.get(1).text()
      }
    }
  }

  private def viewAsDocument(implicit request: FakeRequest[AnyContentAsEmpty.type]) = {
    val homeView: home = app.injector.instanceOf[home]
    Jsoup.parse(homeView.apply().body)
  }
}
