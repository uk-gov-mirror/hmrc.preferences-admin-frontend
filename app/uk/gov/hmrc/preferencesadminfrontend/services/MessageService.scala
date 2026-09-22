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

import play.api.http.Status.*
import play.api.libs.json.*
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.preferencesadminfrontend.connectors.MessageConnector
import uk.gov.hmrc.preferencesadminfrontend.model.{ BatchMessagePreview, GmcBatch, MessagePreview }
import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class MessageService @Inject() (messageConnector: MessageConnector) {

  def getGmcBatches()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[Seq[GmcBatch], String]] =
    messageConnector
      .getGmcBatches()
      .map(response =>
        response.status match {
          case OK =>
            Json.parse(response.body).validate[Seq[GmcBatch]].asOpt match {
              case Some(batches) => Left(batches)
              case None          => Right("The GMC batches retrieved for version v4 do not appear to be valid.")
            }
          case _ => Right(response.body)
        }
      )

  def getRandomMessagePreview(
    batch: GmcBatch
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[BatchMessagePreview, String]] =
    messageConnector
      .getRandomMessagePreview(batch)
      .map(response =>
        response.status match {
          case OK =>
            Json.parse(response.body).validate[MessagePreview].asOpt match {
              case Some(preview) => Left(BatchMessagePreview(preview, batch.batchId))
              case None          => Right("The message preview retrieved does not appear to be valid.")
            }
          case _ => Right(response.body)
        }
      )
}
