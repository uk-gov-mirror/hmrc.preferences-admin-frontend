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

import play.api.Logging
import play.api.data.FormError
import play.api.i18n.I18nSupport
import play.api.libs.json.Json
import play.api.mvc.{ Action, AnyContent, MessagesControllerComponents, Request, Result }
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.preferencesadminfrontend.config.AppConfig
import uk.gov.hmrc.preferencesadminfrontend.model.{ MigrationEntries, MigrationSummary, SummaryItem, SyncEntries }
import uk.gov.hmrc.preferencesadminfrontend.services.Identifier._
import uk.gov.hmrc.preferencesadminfrontend.services.{ Identifier, MigratePreferencesService, MigrationResult }
import uk.gov.hmrc.preferencesadminfrontend.views.html.{ migration_entries, migration_status, migration_summary }

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class MessageController @Inject() (
  authorisedAction: AuthorisedAction,
  migrationEntriesView: migration_entries,
  migrationSummaryView: migration_summary,
  migrationStatusView: migration_status,
  migratePreferencesService: MigratePreferencesService,
  mcc: MessagesControllerComponents
)(implicit appConfig: AppConfig, ec: ExecutionContext)
    extends FrontendController(mcc) with I18nSupport with Logging {

  def show(): Action[AnyContent] = authorisedAction.async { implicit request => _ =>
    Future.successful(Ok(migrationEntriesView(MigrationEntries())))
  }

  def check(): Action[AnyContent] = authorisedAction.async { implicit request => _ =>
    MigrationEntries()
      .bindFromRequest()
      .fold(
        formWithErrors => Future.successful(BadRequest(migrationEntriesView(formWithErrors))),
        input => summaryResult(input.entries)
      )
  }

  def sync(): Action[AnyContent] = authorisedAction.async { implicit request => _ =>
    SyncEntries()
      .bindFromRequest()
      .fold(
        _ => Future.successful(Redirect(routes.MessageController.show())),
        input =>
          request.body.asFormUrlEncoded.flatMap(_.get("accepted")) match {
            case Some(_) =>
              validateIdentities(input.entries)
                .fold(
                  _ => returnEntriesLost(),
                  identifiers =>
                    migratePreferencesService.migrate(identifiers = identifiers, dryRun = false).map { result =>
                      Ok(migrationStatusView(result))
                    }
                )
            case None =>
              validateIdentities(input.entries)
                .fold(
                  _ => returnEntriesLost(),
                  identifiers =>
                    dryRun(identifiers).map { result =>
                      Ok(
                        migrationSummaryView(
                          summary(result),
                          identifiers,
                          SyncEntries()
                            .fill(SyncEntries(Json.toJson(identifiers).toString(), false))
                            .withError(FormError("accepted", "We have to confirm migration"))
                        )
                      )

                    }
                )

          }
      )
  }

  private def validateIdentities(entries: String) =
    Json
      .parse(entries)
      .validate[List[Identifier]]

  private def returnEntriesLost()(implicit request: Request[AnyContent]) =
    Future.successful(
      BadRequest(
        migrationEntriesView(
          MigrationEntries()
            .withError(FormError("identifiers", "We lost identifiers please start again"))
        )
      )
    )

  private def dryRun(identifiers: List[Identifier])(implicit hc: HeaderCarrier) =
    migratePreferencesService.migrate(identifiers, dryRun = true)

  private def summaryResult(entries: String)(implicit request: Request[AnyContent]): Future[Result] =
    parse(entries) match {
      case Right(identifiers) =>
        dryRun(identifiers).map { result =>
          Ok(
            migrationSummaryView(
              summary(result),
              identifiers,
              SyncEntries().fill(SyncEntries(Json.toJson(identifiers).toString(), true))
            )
          )
        }
      case Left(value) =>
        Future.successful(
          BadRequest(migrationEntriesView(MigrationEntries().withError(FormError("identifiers", value))))
        )
    }

  private[controllers] def parse(input: String): Either[String, List[Identifier]] = {
    val lines: List[String] = input.split("\n").toList
    def parseEntries(lines: List[String]): Either[String, List[Identifier]] = {
      def add(line: String): Either[String, Identifier] =
        line.split(",").toList.map(_.trim) match {
          case Nil                                      => Left("empty line")
          case first :: second :: Nil if first.isEmpty  => Left(s"ItsaId is missing for $second")
          case first :: second :: Nil if second.isEmpty => Left(s"Utr is missing for $first")
          case first :: second :: Nil                   => Right(Identifier(first, second))
          case _ :: Nil                                 => Left(s"whitespace")
          case _ :: _ :: _ :: Nil                       => Left(s"only itsaId and utr is required")
          case _ :: _ :: _ :: _                         => Left(s"only itsaId and utr is required")
        }
      def loop(lines: List[String], acc: Either[String, List[Identifier]]): Either[String, List[Identifier]] =
        lines match {
          case line :: tail =>
            val lineResult = add(line).left.flatMap(x => Left(x))
            lineResult.flatMap(item => loop(tail, acc.map(i => i :+ item)))
          case Nil  => acc
          case null => Left("input is empty")
        }
      loop(lines, Right(List.empty))
    }
    parseEntries(lines)
  }

  private def summary(result: List[MigrationResult]) = {
    val total = result
    val group = result.groupBy(_.status)
    val noDigitalFootPrint = group.get("NoDigitalFootprint")
    val saOnline = group.get("SAOnline")
    val ItsaOnlineNoPreference = group.get("ITSAOnlineNoPreference")
    val ItsaOnlinewithPreference = group.get("ITSAOnlinePreference")
    val saItsaCustomer = group.get("SAandITSA")

    MigrationSummary(
      total = SummaryItem(total.size, total),
      noDigitalFootprint =
        SummaryItem(noDigitalFootPrint.getOrElse(List.empty).size, noDigitalFootPrint.getOrElse(List.empty)),
      saOnlineCustomer = SummaryItem(saOnline.getOrElse(List.empty).size, saOnline.getOrElse(List.empty)),
      itsaOnlineNoPreference =
        SummaryItem(ItsaOnlineNoPreference.getOrElse(List.empty).size, ItsaOnlineNoPreference.getOrElse(List.empty)),
      itsaOnlineCustomerPreference = SummaryItem(
        ItsaOnlinewithPreference.getOrElse(List.empty).size,
        ItsaOnlinewithPreference.getOrElse(List.empty)
      ),
      saAndItsaCustomer = SummaryItem(saItsaCustomer.getOrElse(List.empty).size, saItsaCustomer.getOrElse(List.empty))
    )
  }
}
