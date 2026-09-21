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

import cats.syntax.either._
import play.api.Logger
import play.api.libs.json.{ Json, OFormat }
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.preferencesadminfrontend.model.MTDPMigration._

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

case class Identifier(itsaId: String, utr: String)

object Identifier {
  implicit val identifierFormat: OFormat[Identifier] = Json.format[Identifier]
}

class MigratePreferencesService @Inject() (
  customerMigrationResolver: CustomerMigrationResolver,
  customerPreferenceMigrator: CustomerPreferenceMigrator
) {
  val logger = Logger(getClass)
  def migrate(
    identifiers: List[Identifier],
    dryRun: Boolean
  )(implicit headerCarrier: HeaderCarrier, executionContext: ExecutionContext): Future[List[MigrationResult]] = {
    logger.debug(s"migrate identifiers ${identifiers.map(i => i.itsaId + i.utr)} with dryrun $dryRun")
    val results = identifiers.map { identifier =>
      customerMigrationResolver
        .resolveCustomerType(identifier)
        .flatMap(migrate(_, identifier, dryRun))
        .recover { case exception: Exception =>
          logger.warn(s"migrateFunctionOutput ${exception.getMessage}")
          MigrationResult(exception, identifier)
        }
    }

    Future.sequence(results)
  }

  private def migrate(
    result: Either[String, CustomerType],
    identifier: Identifier,
    dryRun: Boolean
  )(implicit headerCarrier: HeaderCarrier, executionContext: ExecutionContext): Future[MigrationResult] = {

    result match {
      case Right(value) => logger.warn(s"migratePrivateSuccessResult $value")
      case Left(value)  => logger.warn(s"migratePrivateFailureResult $value")
    }

    result match {
      case Right(m: MigratingCustomer) => migrateCustomer(identifier, m, dryRun).map(MigrationResult(_, identifier, m))
      case Right(n: NonMigratingCustomer) => Future.successful(MigrationResult(n, identifier))
      case Left(error) => Future.successful(MigrationResult(identifier, SentStatus.Failed, DisplayType.Red, error))
    }
  }

  private def migrateCustomer(identifier: Identifier, migratingCustomer: MigratingCustomer, dryRun: Boolean)(implicit
    headerCarrier: HeaderCarrier,
    executionContext: ExecutionContext
  ): Future[Either[String, Unit]] =
    if (dryRun) {
      Future.successful(().asRight)
    } else {
      logger.debug(s"migrateCustomerprivate ${identifier.itsaId} and $migratingCustomer  ")
      customerPreferenceMigrator.migrateCustomer(identifier, migratingCustomer)
    }
}

case class MessageStatus(utr: String, status: String, displayClass: String, reason: String)
case class MigrationResult(identifier: Identifier, status: String, displayClass: String, reason: String)

object MigrationResult {
  def apply(nonMigratingCustomer: NonMigratingCustomer, identifier: Identifier): MigrationResult =
    new MigrationResult(
      identifier = identifier,
      status = status(nonMigratingCustomer),
      displayClass = DisplayType.Green,
      reason = s"No preference to migrate for ${nonMigratingCustomer.getClass.getSimpleName} customer"
    )

  def apply(
    result: Either[String, Unit],
    identifier: Identifier,
    migratingCustomer: MigratingCustomer
  ): MigrationResult =
    new MigrationResult(
      identifier = identifier,
      status = result.map(_ => status(migratingCustomer)).getOrElse(SentStatus.Failed),
      displayClass = result.map(_ => DisplayType.Green).getOrElse(DisplayType.Red),
      reason = result.map(_ => SentStatus.Sent).valueOr(left => left)
    )

  def apply(exception: Exception, identifier: Identifier): MigrationResult =
    new MigrationResult(
      identifier = identifier,
      status = SentStatus.Failed,
      displayClass = DisplayType.Red,
      reason = exception.getMessage
    )

  def status(customerType: CustomerType): String = customerType.getClass.getSimpleName.stripSuffix("$")
}

object DisplayType {
  val Red = "red"
  val Green = "green"
}

object SentStatus {
  val Sent = "Sent"
  val NoPreferenceToMigrate = "No preference to migrate"
  val Failed = "unsuccessful"
}
