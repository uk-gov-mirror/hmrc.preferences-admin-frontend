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
import play.api.mvc._
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.preferencesadminfrontend.config.AppConfig
import uk.gov.hmrc.preferencesadminfrontend.connectors.MessageConnector
import uk.gov.hmrc.preferencesadminfrontend.model.{ GmcBatch, GmcBatchApproval }
import uk.gov.hmrc.preferencesadminfrontend.services.MessageService
import uk.gov.hmrc.preferencesadminfrontend.views.html.{ ErrorTemplate, batch_approval, batch_rejection, message_brake_admin }

import scala.concurrent.{ ExecutionContext, Future }

class MessageBrakeController @Inject() (
  authorisedAction: AuthorisedAction,
  messageConnector: MessageConnector,
  messageService: MessageService,
  mcc: MessagesControllerComponents,
  errorTemplateView: ErrorTemplate,
  batchApprovalView: batch_approval,
  batchRejectionView: batch_rejection,
  messageBrakeAdminView: message_brake_admin
)(implicit appConfig: AppConfig, ec: ExecutionContext)
    extends FrontendController(mcc) with I18nSupport with Logging with RoleAuthorisedAction(authorisedAction) {

  override def role: Role = Role.Admin

  def showAdminPage: Action[AnyContent] = authorisedAction { implicit request => _ =>
    messageService.getGmcBatches().map {
      case Left(batches) => Ok(messageBrakeAdminView(batches))
      case Right(error)  => returnError(error)
    }
  }

  def previewMessage: Action[AnyContent] = authorisedAction { implicit request => _ =>
    val batch = GmcBatch().bindFromRequest().get
    for {
      gmcBatches     <- messageService.getGmcBatches()
      messagePreview <- messageService.getRandomMessagePreview(batch)
    } yield gmcBatches match {
      case Left(batches) =>
        messagePreview match {
          case Left(preview) => Ok(messageBrakeAdminView(batches, Some(preview)))
          case Right(error)  => returnError(error)
        }
      case Right(error) => returnError(error)
    }
  }

  def showApproveBatchConfirmationPage(): Action[AnyContent] = authorisedAction { implicit request => _ =>
    Future.successful(Ok(batchApprovalView(GmcBatchApproval().bindFromRequest().discardingErrors)))
  }

  def showRejectBatchConfirmationPage(): Action[AnyContent] = authorisedAction { implicit request => _ =>
    Future.successful(Ok(batchRejectionView(GmcBatchApproval().bindFromRequest().discardingErrors)))
  }

  private def approveOrRejectBatches(
    batches: Seq[GmcBatch],
    gmcBatchApproval: GmcBatchApproval,
    approveReject: GmcBatchApproval => Future[HttpResponse]
  ): Future[Option[HttpResponse]] = {
    val filteredBatches = batches
      .filter(b => gmcBatchApproval.batchId.split(",").toList.contains(b.batchId))
      .map(GmcBatchApproval(_, gmcBatchApproval.reasonText))
    val result = Future.traverse(filteredBatches)(approveReject)
    result.map(httpResponses => httpResponses.find(_.status != OK).orElse(httpResponses.headOption))
  }

  def confirmApproveBatch: Action[AnyContent] = authorisedAction { implicit request => _ =>
    GmcBatchApproval()
      .bindFromRequest()
      .fold(
        formWithErrors => Future.successful(BadRequest(batchApprovalView(formWithErrors))),
        gmcBatchApproval =>
          for {
            batchesResult <- messageService.getGmcBatches()
            result <- batchesResult match {
                        case Left(batches) =>
                          approveOrRejectBatches(batches, gmcBatchApproval, messageConnector.approveGmcBatch)
                        case Right(_) => Future.successful(None)
                      }
            newBatches <- messageService.getGmcBatches()
          } yield result match {
            case Some(res) if res.status == OK =>
              newBatches match {
                case Left(batches) => Ok(messageBrakeAdminView(batches))
                case Right(error)  => returnError(error)
              }
            case _ => returnError("Failed to approve batch.")
          }
      )
  }

  def confirmRejectBatch: Action[AnyContent] = authorisedAction { implicit request => _ =>
    GmcBatchApproval()
      .bindFromRequest()
      .fold(
        formWithErrors => Future.successful(BadRequest(batchRejectionView(formWithErrors))),
        gmcBatchApproval =>
          for {
            batchesResult <- messageService.getGmcBatches()
            result <- batchesResult match {
                        case Left(batches) =>
                          approveOrRejectBatches(batches, gmcBatchApproval, messageConnector.rejectGmcBatch)
                        case Right(_) => Future.successful(None)
                      }
            newBatches <- messageService.getGmcBatches()
          } yield result match {
            case Some(res) if res.status == OK =>
              newBatches match {
                case Left(batches) => Ok(messageBrakeAdminView(batches))
                case Right(error)  => returnError(error)
              }
            case _ => returnError("Failed to reject batch.")
          }
      )
  }

  private def returnError(error: String)(implicit request: Request[_]): Result =
    BadGateway(errorTemplateView("Error", "There was an error:", error))

}
