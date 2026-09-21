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

package uk.gov.hmrc.preferencesadminfrontend.model

import com.google.common.io.BaseEncoding
import play.api.libs.json.{ Json, Reads }
import play.twirl.api.Html

case class MessagePreview(
  subject: String,
  welshSubject: Option[String],
  content: String,
  welshContent: Option[String],
  externalRefId: String,
  messageType: String,
  issueDate: String,
  taxIdentifierName: String
)

case class BatchMessagePreview(message: MessagePreview, batchId: String) {
  def getContentHtml: Html =
    Html(new String(BaseEncoding.base64().decode(message.content)))

  def getWelshContentHtml: Html = {
    val welshContent = message.welshContent.map(c => new String(BaseEncoding.base64().decode(c))).getOrElse("")
    Html(welshContent)
  }
}

object MessagePreview {
  implicit val messagePreviewReads: Reads[MessagePreview] = Json.reads[MessagePreview]
}
