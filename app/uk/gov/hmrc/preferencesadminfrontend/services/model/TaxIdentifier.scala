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

import play.api.libs.json.{ Json, OFormat }

case class TaxIdentifier(name: String, value: String) {
  val regime: String = name match {
    case "sautr"       => "sa"
    case "itsa"        => "itsa"
    case "nino"        => "paye"
    case "email"       => "email"
    case "HMRC-MTD-IT" => "itsa"
    case _             => throw new RuntimeException(s"Invalid tax id name '$name'")
  }
}

object TaxIdentifier {
  implicit val format: OFormat[TaxIdentifier] = Json.format[TaxIdentifier]

  def ninoIdentifier(value: String): TaxIdentifier =
    TaxIdentifier("nino", value)
}
