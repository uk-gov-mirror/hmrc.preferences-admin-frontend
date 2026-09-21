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

import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{ never, verify, when }
import org.scalatest.OptionValues
import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{ JsSuccess, Json }
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.preferencesadminfrontend.model.MTDPMigration.{ MigratingCustomer, NonMigratingCustomer }

import scala.concurrent.{ ExecutionContext, Future }

class MigratePreferencesServiceSpec
    extends PlaySpec with OptionValues with MockitoSugar with ScalaFutures with IntegrationPatience {

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  "Identifier" must {

    "deserialize from JSON (Reads)" in {
      val jsonString =
        """{
          "itsaId": "ITSA-123",
          "utr": "UTR-456"
        }"""

      val result = Json.parse(jsonString).validate[Identifier]

      result mustBe JsSuccess(Identifier("ITSA-123", "UTR-456"))
    }

    "serialize to JSON (Writes)" in {
      val identifier = Identifier("ITSA-123", "UTR-456")

      val result = Json.toJson(identifier)

      (result \ "itsaId").as[String] mustBe "ITSA-123"
      (result \ "utr").as[String] mustBe "UTR-456"
    }
  }

  "migrate" must {

    "return successful result for migrating customer when dryRun is true and not call migrator" in new TestCase {
      when(mockResolver.resolveCustomerType(ArgumentMatchers.eq(identifier))(any[HeaderCarrier]))
        .thenReturn(Future.successful(Right(migratingCustomer)))

      val result = service.migrate(List(identifier), dryRun = true).futureValue

      result must have size 1
      result.head.identifier mustBe identifier
      result.head.status mustBe MigrationResult.status(migratingCustomer)
      result.head.displayClass mustBe DisplayType.Green
      result.head.reason mustBe SentStatus.Sent

      verify(mockMigrator, never())
        .migrateCustomer(any[Identifier], any[MigratingCustomer])(any[HeaderCarrier], any[ExecutionContext])
    }

    "return successful result for migrating customer when migrator returns Right(())" in new TestCase {
      when(mockResolver.resolveCustomerType(ArgumentMatchers.eq(identifier))(any[HeaderCarrier]))
        .thenReturn(Future.successful(Right(migratingCustomer)))
      when(
        mockMigrator.migrateCustomer(ArgumentMatchers.eq(identifier), ArgumentMatchers.eq(migratingCustomer))(
          any[HeaderCarrier],
          any[ExecutionContext]
        )
      ).thenReturn(Future.successful(Right(())))

      val result = service.migrate(List(identifier), dryRun = false).futureValue

      result must have size 1
      result.head.identifier mustBe identifier
      result.head.status mustBe MigrationResult.status(migratingCustomer)
      result.head.displayClass mustBe DisplayType.Green
      result.head.reason mustBe SentStatus.Sent

      verify(mockMigrator)
        .migrateCustomer(ArgumentMatchers.eq(identifier), ArgumentMatchers.eq(migratingCustomer))(
          any[HeaderCarrier],
          any[ExecutionContext]
        )
    }

    "return failed result for migrating customer when migrator returns Left(error)" in new TestCase {
      val error = "migrate failed"
      when(mockResolver.resolveCustomerType(ArgumentMatchers.eq(identifier))(any[HeaderCarrier]))
        .thenReturn(Future.successful(Right(migratingCustomer)))
      when(
        mockMigrator.migrateCustomer(ArgumentMatchers.eq(identifier), ArgumentMatchers.eq(migratingCustomer))(
          any[HeaderCarrier],
          any[ExecutionContext]
        )
      ).thenReturn(Future.successful(Left(error)))

      val result = service.migrate(List(identifier), dryRun = false).futureValue

      result must have size 1
      result.head.identifier mustBe identifier
      result.head.status mustBe SentStatus.Failed
      result.head.displayClass mustBe DisplayType.Red
      result.head.reason mustBe error

      verify(mockMigrator)
        .migrateCustomer(ArgumentMatchers.eq(identifier), ArgumentMatchers.eq(migratingCustomer))(
          any[HeaderCarrier],
          any[ExecutionContext]
        )
    }

    "return no-preference result for non-migrating customer and not call migrator" in new TestCase {
      when(mockResolver.resolveCustomerType(ArgumentMatchers.eq(identifier))(any[HeaderCarrier]))
        .thenReturn(Future.successful(Right(nonMigratingCustomer)))

      val result = service.migrate(List(identifier), dryRun = false).futureValue

      result must have size 1
      result.head.identifier mustBe identifier
      result.head.status mustBe MigrationResult.status(nonMigratingCustomer)
      result.head.displayClass mustBe DisplayType.Green
      result.head.reason must include("No preference to migrate")

      verify(mockMigrator, never())
        .migrateCustomer(any[Identifier], any[MigratingCustomer])(any[HeaderCarrier], any[ExecutionContext])
    }

    "return failed result when resolver returns Left(error) and not call migrator" in new TestCase {
      val error = "resolver error"
      when(mockResolver.resolveCustomerType(ArgumentMatchers.eq(identifier))(any[HeaderCarrier]))
        .thenReturn(Future.successful(Left(error)))

      val result = service.migrate(List(identifier), dryRun = false).futureValue

      result must have size 1
      result.head.identifier mustBe identifier
      result.head.status mustBe SentStatus.Failed
      result.head.displayClass mustBe DisplayType.Red
      result.head.reason mustBe error

      verify(mockMigrator, never())
        .migrateCustomer(any[Identifier], any[MigratingCustomer])(any[HeaderCarrier], any[ExecutionContext])
    }

    "recover to failed result when resolver future fails with exception and not call migrator" in new TestCase {
      val ex = new RuntimeException("failed")
      when(mockResolver.resolveCustomerType(ArgumentMatchers.eq(identifier))(any[HeaderCarrier]))
        .thenReturn(Future.failed(ex))

      val result = service.migrate(List(identifier), dryRun = false).futureValue

      result must have size 1
      result.head.identifier mustBe identifier
      result.head.status mustBe SentStatus.Failed
      result.head.displayClass mustBe DisplayType.Red
      result.head.reason mustBe "failed"

      verify(mockMigrator, never())
        .migrateCustomer(any[Identifier], any[MigratingCustomer])(any[HeaderCarrier], any[ExecutionContext])
    }
  }

  class TestCase {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val mockResolver: CustomerMigrationResolver = mock[CustomerMigrationResolver]
    val mockMigrator: CustomerPreferenceMigrator = mock[CustomerPreferenceMigrator]

    val migratingCustomer: MigratingCustomer = mock[MigratingCustomer]
    val nonMigratingCustomer: NonMigratingCustomer = mock[NonMigratingCustomer]

    val service = new MigratePreferencesService(mockResolver, mockMigrator)

    val identifier: Identifier = Identifier("ITSA-123", "UTR-456")
  }
}
