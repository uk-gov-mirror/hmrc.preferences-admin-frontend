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
import play.api.i18n.I18nSupport
import play.api.mvc.*
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.preferencesadminfrontend.config.AppConfig
import uk.gov.hmrc.preferencesadminfrontend.controllers.model.SearchNinos
import uk.gov.hmrc.preferencesadminfrontend.services.*
import uk.gov.hmrc.preferencesadminfrontend.views.html.*

import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class MultiSearchController @Inject() (
  authorisedAction: AuthorisedAction,
  decoderView: decode,
  multiSearchView: multi_search,
  searchResultsView: search_results,
  searchService: SearchService,
  val mcc: MessagesControllerComponents
)(implicit
  appConfig: AppConfig,
  ec: ExecutionContext
) extends FrontendController(mcc) with I18nSupport with Logging with RoleAuthorisedAction(authorisedAction) {

  override def role: Role = Role.Admin

  val showDecodePage: Action[AnyContent] = authorisedAction { implicit request => _ =>
    Future.successful(Ok(decoderView()))
  }

  val showMultiSearchPage: Action[AnyContent] = authorisedAction { implicit request => _ =>
    Future.successful(Ok(multiSearchView()))
  }

  def showResultsPage(): Action[AnyContent] = authorisedAction.async { implicit request => implicit user =>
    SearchNinos()
      .bindFromRequest()
      .fold(
        errors => Future.successful(BadRequest(searchResultsView(List.empty, Some(s"Error found - $errors")))),
        searchTaxIdentifier =>
          searchService.searchPreferences(searchTaxIdentifier.identifiers).map {
            case Nil =>
              Ok(
                searchResultsView(Nil, None)
              )
            case preferenceList =>
              Ok(searchResultsView(preferenceList, None))
          }
      )
  }
}
