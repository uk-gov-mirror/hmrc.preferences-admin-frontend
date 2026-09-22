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

import org.mockito.ArgumentMatchers
import org.mockito.Mockito.{ times, verify, when }
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.mvc.MessagesControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers.status
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.preferencesadminfrontend.config.AppConfig
import uk.gov.hmrc.preferencesadminfrontend.controllers.model.User
import uk.gov.hmrc.preferencesadminfrontend.services.{ Identifier, MigratePreferencesService, MigrationResult }
import uk.gov.hmrc.preferencesadminfrontend.views.html.{ migration_entries, migration_status, migration_summary }
import org.mockito.ArgumentMatchers._
import play.api.libs.json.Json
import scala.concurrent.{ ExecutionContext, Future }

class MessageControllerSpec extends PlaySpec with GuiceOneAppPerSuite {
  val controller = app.injector.instanceOf[MessageController]

  "parseEntries function" must {
    "convert to Identifier" in {
      val entries = "XFIT00000004173,123456789\nXFIT00000004173,123456789\nXFIT00000004173,123456789"
      controller.parse(entries) mustBe (Right(
        List(
          Identifier("XFIT00000004173", "123456789"),
          Identifier("XFIT00000004173", "123456789"),
          Identifier("XFIT00000004173", "123456789")
        )
      ))
    }

    "convert to 1 Identifier" in {
      val entries = "XFIT00000004173,123456789"
      controller.parse(entries) mustBe Right(List(Identifier("XFIT00000004173", "123456789")))
    }

    "handle white space" in {
      val entries = "XFIT00000004173,123456789\nXFIT00000004173,"
      controller.parse(entries) mustBe (Left("whitespace"))
    }

    "error if itsaId is empty" in {
      val entries = "XFIT00000004173,123456789\n,123456789"
      controller.parse(entries) mustBe (Left("ItsaId is missing for 123456789"))
    }
    "error for empty line" in {
      val entries = "XFIT00000004173,123456789\n,"
      controller.parse(entries) mustBe (Left("empty line"))
    }
    "error if 3 values is supplied" in {
      val entries = "XFIT00000004173,123456789,whatever"
      controller.parse(entries) mustBe (Left("only itsaId and utr is required"))
    }

  }

  "check function" must {
    "only do a dry run" in new TestClass {
      when(
        migratePreferenceServiceMock
          .migrate(ArgumentMatchers.eq(List(Identifier("1", "2"))), ArgumentMatchers.eq(true))(
            any[HeaderCarrier],
            any[ExecutionContext]
          )
      )
        .thenReturn(Future.successful(List(MigrationResult(Identifier("", ""), "", "", ""))))
      val controller = new MessageController(
        authorisedAction,
        migrationEntriesView,
        migrationSummaryView,
        migrationStatusView,
        migratePreferenceServiceMock,
        messagesControllerComponents
      )(appConfig, executionContext)

      val result = controller.check()(fakeRequestWithSession)
      status(result) mustBe 200
      verify(migratePreferenceServiceMock, times(1))
        .migrate(any[List[Identifier]], any[Boolean])(any[HeaderCarrier], any[ExecutionContext])
    }
  }

  "sync function" must {
    "only do a dry run when sync is not confirmed" in new TestClass {
      val identifiers = Json.toJson(List(Identifier("1", "2"))).toString()
      val fakeRequest = FakeRequest(routes.MessageController.sync())
        .withSession(User.sessionKey -> "user")
        .withFormUrlEncodedBody("entries" -> identifiers)

      when(
        migratePreferenceServiceMock
          .migrate(ArgumentMatchers.eq(List(Identifier("1", "2"))), ArgumentMatchers.eq(true))(
            any[HeaderCarrier],
            any[ExecutionContext]
          )
      )
        .thenReturn(Future.successful(List(MigrationResult(Identifier("", ""), "", "", ""))))
      val controller = new MessageController(
        authorisedAction,
        migrationEntriesView,
        migrationSummaryView,
        migrationStatusView,
        migratePreferenceServiceMock,
        messagesControllerComponents
      )(appConfig, executionContext)

      val result = controller.sync()(fakeRequest)
      status(result) mustBe 200
      verify(migratePreferenceServiceMock, times(1))
        .migrate(any[List[Identifier]], any[Boolean])(any[HeaderCarrier], any[ExecutionContext])
    }
    "do dryRun false when sync is confirmed" in new TestClass {
      val identifiers = Json.toJson(List(Identifier("1", "2"))).toString()
      val fakeRequest = FakeRequest(routes.MessageController.sync())
        .withSession(User.sessionKey -> "user")
        .withFormUrlEncodedBody("entries" -> identifiers, "accepted" -> "true")

      when(
        migratePreferenceServiceMock
          .migrate(ArgumentMatchers.eq(List(Identifier("1", "2"))), ArgumentMatchers.eq(false))(
            any[HeaderCarrier],
            any[ExecutionContext]
          )
      )
        .thenReturn(Future.successful(List(MigrationResult(Identifier("", ""), "", "", ""))))
      val controller = new MessageController(
        authorisedAction,
        migrationEntriesView,
        migrationSummaryView,
        migrationStatusView,
        migratePreferenceServiceMock,
        messagesControllerComponents
      )(appConfig, executionContext)

      val result = controller.sync()(fakeRequest)
      status(result) mustBe 200
      verify(migratePreferenceServiceMock, times(1))
        .migrate(any[List[Identifier]], any[Boolean])(any[HeaderCarrier], any[ExecutionContext])
    }
  }

  class TestClass {
    val entries = "1,2"
    val fakeRequestWithSession =
      FakeRequest(routes.MessageController.check())
        .withSession(User.sessionKey -> "user")
        .withFormUrlEncodedBody("identifiers" -> entries)

    val authorisedAction = app.injector.instanceOf[AuthorisedAction]
    val migrationEntriesView = app.injector.instanceOf[migration_entries]
    val migrationSummaryView = app.injector.instanceOf[migration_summary]
    val migrationStatusView = app.injector.instanceOf[migration_status]

    val migratePreferenceServiceMock = mock[MigratePreferencesService]
    val messagesControllerComponents = app.injector.instanceOf[MessagesControllerComponents]
    implicit val appConfig: AppConfig = app.injector.instanceOf[AppConfig]
    implicit val executionContext: ExecutionContext = app.injector.instanceOf[ExecutionContext]
  }
}
