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

import javax.inject.{ Inject, Singleton }
import play.api.{ Configuration, Logging }
import play.api.data.Form
import play.api.data.Forms.{ mapping, * }
import play.api.i18n.I18nSupport
import play.api.mvc.*
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.play.bootstrap.config.AppName
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.preferencesadminfrontend.config.AppConfig
import uk.gov.hmrc.preferencesadminfrontend.controllers.Role.{ Admin, Generic, SolsGeneric }
import uk.gov.hmrc.preferencesadminfrontend.controllers.model.User
import uk.gov.hmrc.preferencesadminfrontend.services.LoginService
import uk.gov.hmrc.preferencesadminfrontend.views.html.login

import java.time.Instant
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class LoginController @Inject() (
  authorisedAction: AuthorisedAction,
  loginService: LoginService,
  auditConnector: AuditConnector,
  config: Configuration,
  mcc: MessagesControllerComponents,
  loginView: login
)(implicit appConfig: AppConfig, ec: ExecutionContext)
    extends FrontendController(mcc) with I18nSupport with Logging {

  def showLoginPage(): Action[AnyContent] = Action.async { implicit request =>
    val sessionUpdated = request.session + ("ts" -> Instant.now.toEpochMilli.toString)
    Future.successful(Ok(loginView(userForm)).withSession(sessionUpdated))
  }

  def loginAction(): Action[AnyContent] = Action.async { implicit request =>
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequestAndSession(request, request.session)

    userForm
      .bindFromRequest()
      .fold(
        formWithErrors => Future.successful(BadRequest(loginView(formWithErrors))),
        userData =>
          if (loginService.isAuthorised(userData)) {
            auditConnector.sendEvent(createLoginEvent(userData.username, true))
            val sessionUpdated = updateSessionParamByLoggedInUserRoleAndTimeStamp(request, userData)

            Future.successful(Redirect(routes.HomeController.showHomePage()).withSession(sessionUpdated))
          } else {
            auditConnector.sendEvent(createLoginEvent(userData.username, false))
            val userFormWithErrors = userForm.fill(userData).withGlobalError("error.credentials.invalid")

            Future.successful(Unauthorized(loginView(userFormWithErrors)))
          }
      )
  }

  private def updateSessionParamByLoggedInUserRoleAndTimeStamp(request: MessagesRequest[AnyContent], userData: User) =
    request.session
      + (User.sessionKey -> userData.username)
      + ("ts"            -> Instant.now.toEpochMilli.toString)
      + ("isAdmin"       -> loginService.hasRequiredRole(userData, Admin).toString)
      + ("isSols"        -> loginService.hasRequiredRole(userData, SolsGeneric).toString)
      + ("isGeneric"     -> loginService.hasRequiredRole(userData, Generic).toString)

  def logoutAction(): Action[AnyContent] = authorisedAction.async { implicit request => user =>
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequestAndSession(request, request.session)
    auditConnector.sendEvent(createLogoutEvent(user.username))
    Future.successful(Redirect(routes.LoginController.showLoginPage()).withSession(Session()))
  }

  private def createLoginEvent(username: String, successful: Boolean): DataEvent = DataEvent(
    auditSource = AppName.fromConfiguration(config),
    auditType = if (successful) "TxSucceeded" else "TxFailed",
    detail = Map("user" -> username),
    tags = Map("transactionName" -> "Login")
  )

  private def createLogoutEvent(username: String): DataEvent = DataEvent(
    auditSource = AppName.fromConfiguration(config),
    auditType = "TxSucceeded",
    detail = Map("user" -> username),
    tags = Map("transactionName" -> "Logout")
  )

  val userForm = Form(
    mapping(
      "username" -> nonEmptyText,
      "password" -> nonEmptyText
    )((userName, password) => User(userName, password, List.empty[Role]))(user => Some(user.username, user.password))
  )
}
