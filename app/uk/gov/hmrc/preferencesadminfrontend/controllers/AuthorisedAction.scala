/*
 * Copyright 2023 HM Revenue & Customs
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

import play.api.mvc.{ Action, AnyContent, MessagesBaseController, MessagesControllerComponents, Request, Result }
import uk.gov.hmrc.preferencesadminfrontend.controllers.Role.Generic
import uk.gov.hmrc.preferencesadminfrontend.controllers.model.User
import uk.gov.hmrc.preferencesadminfrontend.services.LoginService

import javax.inject.Inject
import scala.concurrent.Future

class AuthorisedAction @Inject() (loginService: LoginService, val controllerComponents: MessagesControllerComponents)
    extends MessagesBaseController {

  def async(block: Request[AnyContent] => User => Future[Result]): Action[AnyContent] = async(Generic)(block)

  def async(role: Role)(
    block: Request[AnyContent] => User => Future[Result]
  ): Action[AnyContent] =
    Action.async { implicit request =>
      val isAdmin = request.session.get("isAdmin").getOrElse("false").toBoolean
      val user = request.session.get(User.sessionKey).map(name => User(name, ""))

      user match {
        case Some(user) if hasRequiredRoleOrBulkOptOutsAccess(user, role) || isAdmin => block(request)(user)
        case _ => Future.successful(Redirect(routes.LoginController.showLoginPage()))
      }
    }

  private def hasRequiredRoleOrBulkOptOutsAccess(user: User, role: Role)(implicit request: Request[_]) =
    if (request.uri.equals(routes.CsvUploadBulkOptOutsController.showBulkOptOutsUploadPage.url)) {
      loginService.hasRequiredRole(user, role) && hasAccessForCsvUploadBulkOptOuts
    } else {
      loginService.hasRequiredRole(user, role)
    }

  private def hasAccessForCsvUploadBulkOptOuts(implicit request: Request[_]): Boolean = {
    val isAdmin = request.session.get("isAdmin").getOrElse("false").toBoolean
    val isGeneric = request.session.get("isGeneric").getOrElse("false").toBoolean
    val isSols = request.session.get("isSols").getOrElse("false").toBoolean

    (isGeneric || isAdmin) && (!isSols)
  }
}
