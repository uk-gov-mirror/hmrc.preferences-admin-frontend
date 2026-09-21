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

import javax.inject.Inject
import play.api.i18n.I18nSupport
import play.api.libs.json.Json
import play.api.mvc.{ Action, AnyContent, MessagesControllerComponents }
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.preferencesadminfrontend.config.AppConfig
import uk.gov.hmrc.preferencesadminfrontend.connectors.MessageConnector
import uk.gov.hmrc.preferencesadminfrontend.model.Allowlist.*
import uk.gov.hmrc.preferencesadminfrontend.model.{ Allowlist, AllowlistEntry }
import uk.gov.hmrc.preferencesadminfrontend.views.html.{ ErrorTemplate, allowlist_add, allowlist_delete, allowlist_show }

import scala.concurrent.{ ExecutionContext, Future }

class AllowlistController @Inject() (
  authorisedAction: AuthorisedAction,
  messageConnector: MessageConnector,
  mcc: MessagesControllerComponents,
  errorTemplateView: ErrorTemplate,
  allowlistAddView: allowlist_add,
  allowlistShowView: allowlist_show,
  allowlistDeleteView: allowlist_delete
)(implicit appConfig: AppConfig, ec: ExecutionContext)
    extends FrontendController(mcc) with I18nSupport with Logging with RoleAuthorisedAction(authorisedAction) {

  override def role: Role = Role.Admin

  def showAllowlistPage(): Action[AnyContent] = authorisedAction { implicit request => _ =>
    messageConnector
      .getAllowlist()
      .map(response =>
        response.status match {
          case OK =>
            Json.parse(response.body).validate[Allowlist].asOpt match {
              case Some(allowlist) => Ok(allowlistShowView(allowlist))
              case None =>
                BadGateway(
                  errorTemplateView("Error", "There was an error:", "The allowlist does not appear to be valid.")
                )
            }
          case _ => BadGateway(errorTemplateView("Error", "There was an error:", response.body))
        }
      )
  }

  def addFormId: Action[AnyContent] = authorisedAction { implicit request => _ =>
    Future.successful(Ok(allowlistAddView(AllowlistEntry())))
  }

  def confirmAdd: Action[AnyContent] = authorisedAction { implicit request => _ =>
    AllowlistEntry()
      .bindFromRequest()
      .fold(
        formWithErrors => Future.successful(BadRequest(allowlistAddView(formWithErrors))),
        addEntry =>
          if (appConfig.validFormIds.contains(addEntry.formId.toUpperCase))
            messageConnector
              .addFormIdToAllowlist(addEntry)
              .map(response =>
                response.status match {
                  case CREATED => Redirect(routes.AllowlistController.showAllowlistPage())
                  case _       => BadGateway(errorTemplateView("Error", "There was an error:", response.body))
                }
              )
          else
            Future.successful(
              BadRequest(
                errorTemplateView(
                  "Error",
                  "Invalid Form ID",
                  s"This Form ID ${addEntry.formId} is not added in the message break allow list."
                )
              )
            )
      )
  }

  def deleteFormId(formId: String): Action[AnyContent] = authorisedAction { implicit request => _ =>
    Future.successful(Ok(allowlistDeleteView(AllowlistEntry().fill(AllowlistEntry(formId, "")))))
  }

  def confirmDelete: Action[AnyContent] = authorisedAction { implicit request => _ =>
    AllowlistEntry()
      .bindFromRequest()
      .fold(
        formWithErrors => Future.successful(BadRequest(allowlistDeleteView(formWithErrors))),
        deleteEntry =>
          messageConnector
            .deleteFormIdFromAllowlist(deleteEntry)
            .map(response =>
              response.status match {
                case OK => Redirect(routes.AllowlistController.showAllowlistPage())
                case _  => BadGateway(errorTemplateView("Error", "There was an error:", response.body))
              }
            )
      )
  }

}
