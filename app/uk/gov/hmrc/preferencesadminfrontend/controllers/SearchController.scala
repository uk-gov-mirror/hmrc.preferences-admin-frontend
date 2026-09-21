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

import javax.inject.{ Inject, Singleton }
import play.api.i18n.{ I18nSupport, Messages }
import play.api.mvc.{ Action, AnyContent, MessagesControllerComponents, Request, Result }
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.preferencesadminfrontend.config.AppConfig
import uk.gov.hmrc.preferencesadminfrontend.connectors.{ AlreadyOptedOut, OptedOut, PreferenceNotFound }
import uk.gov.hmrc.preferencesadminfrontend.controllers.model.{ OptOutReasonWithIdentifier, Search }
import uk.gov.hmrc.preferencesadminfrontend.services.*
import uk.gov.hmrc.preferencesadminfrontend.services.model.TaxIdentifier
import uk.gov.hmrc.preferencesadminfrontend.views.html.{ confirmed, customer_identification, failed, user_opt_out }

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class SearchController @Inject() (
  authorisedAction: AuthorisedAction,
  searchService: SearchService,
  mcc: MessagesControllerComponents,
  confirmedView: confirmed,
  customerIdentificationView: customer_identification,
  failedView: failed,
  userOptOutView: user_opt_out
)(implicit appConfig: AppConfig, ec: ExecutionContext)
    extends FrontendController(mcc) with I18nSupport with Logging with RoleAuthorisedAction(authorisedAction) {

  override def role: Role = Role.Generic

  def showSearchPage(): Action[AnyContent] =
    authorisedAction.async { implicit request => _ =>
      Future.successful(
        Ok(
          customerIdentificationView(
            Search()
              .bind(Map("name" -> "", "value" -> ""))
              .discardingErrors
          )
        )
      )
    }

  def search(): Action[AnyContent] = authorisedAction { implicit request => implicit user =>
    Search()
      .bindFromRequest()
      .fold(
        errors => Future.successful(BadRequest(customerIdentificationView(errors))),
        searchTaxIdentifier =>
          searchService.searchPreference(searchTaxIdentifier).map {
            case Nil =>
              Ok(
                customerIdentificationView(Search().bindFromRequest().withError("value", "error.preference_not_found"))
              )
            case preferenceList =>
              Ok(userOptOutView(OptOutReasonWithIdentifier(), preferenceList))
          }
      )
  }

  def optOut(): Action[AnyContent] = authorisedAction { implicit request => implicit user =>
    OptOutReasonWithIdentifier()
      .bindFromRequest()
      .fold(
        errors => {
          val identifier = TaxIdentifier(errors.data("identifierName"), errors.data("identifierValue"))
          searchService.getPreference(identifier).map {
            case Nil =>
              Ok(
                customerIdentificationView(
                  Search().bindFromRequest().withError("value", Messages("error.preference_not_found"))
                )
              )
            case preferences =>
              Ok(userOptOutView(errors, preferences))
          }
        },
        optOutReason => {
          val identifier = TaxIdentifier(optOutReason.identifierName, optOutReason.identifierValue)
          searchService.optOut(identifier, optOutReason.reason).flatMap {
            case OptedOut => searchConfirmed(identifier)
            case AlreadyOptedOut =>
              searchFailed(identifier, AlreadyOptedOut.errorCode)
            case PreferenceNotFound =>
              searchFailed(identifier, PreferenceNotFound.errorCode)
          }
        }
      )
  }

  def searchConfirmed(
    taxIdentifier: TaxIdentifier
  )(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Result] =
    searchService.getPreference(taxIdentifier).map {
      case Nil =>
        Ok(failedView(taxIdentifier, Nil, PreferenceNotFound.errorCode))
      case preferences =>
        Ok(confirmedView(preferences))
    }

  def searchFailed(taxIdentifier: TaxIdentifier, failureCode: String)(implicit
    request: Request[AnyContent],
    hc: HeaderCarrier
  ): Future[Result] =
    searchService.getPreference(taxIdentifier).map { preference =>
      Ok(failedView(taxIdentifier, preference, failureCode))
    }
}
