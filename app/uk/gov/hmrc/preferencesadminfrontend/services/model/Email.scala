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

package uk.gov.hmrc.preferencesadminfrontend.services.model

import play.api.libs.functional.syntax._
import play.api.libs.json._

import java.time.ZonedDateTime

case class Email(
  address: String,
  verified: Boolean,
  verifiedOn: Option[ZonedDateTime],
  language: Option[String],
  hasBounces: Boolean,
  pendingEmail: Option[String]
)

object Email {
  implicit val dateFormatDefault: Format[ZonedDateTime] = new Format[ZonedDateTime] {
    override def reads(json: JsValue): JsResult[ZonedDateTime] = Reads.DefaultZonedDateTimeReads.reads(json)
    override def writes(o: ZonedDateTime): JsValue = Writes.ZonedDateTimeEpochMilliWrites.writes(o)
  }
  implicit val writes: Writes[Email] = Json.writes[Email]

  implicit val reads: Reads[Email] = (
    (JsPath \ "email").read[String] and
      (JsPath \ "status").read[String] and
      (JsPath \ "verifiedOn").readNullable[ZonedDateTime] and
      (JsPath \ "language").readNullable[String] and
      (JsPath \ "hasBounces").read[Boolean] and
      (JsPath \ "pendingEmail").readNullable[String]
  ) { (address, status, verifiedOn, language, hasBounces, pendingEmail) =>
    val verified = status == "verified"
    Email(address, verified, verifiedOn, language, hasBounces, pendingEmail)
  }
}
