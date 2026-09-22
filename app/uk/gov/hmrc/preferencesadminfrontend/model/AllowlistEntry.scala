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

import play.api.data.{ Form, Forms, Mapping }
import play.api.data.Forms._
import play.api.data.validation.{ Constraint, Invalid, Valid, ValidationError }
import play.api.libs.json.{ Json, OWrites }

case class AllowlistEntry(formId: String, reasonText: String)

object AllowlistEntry {

  val reasonTextConstraint: Constraint[String] = Constraint("constraints.reasonText") { reasonText =>
    if (reasonText.isEmpty) {
      Invalid("A reason is required")
    } else if (reasonText.matches("[a-zA-Z0-9\\s\\-\\/\\.,:;'&+@#()]+")) {
      Valid
    } else {
      Invalid("Invalid characters entered")
    }
  }

  implicit val writes: OWrites[AllowlistEntry] = Json.writes[AllowlistEntry]

  def apply(): Form[AllowlistEntry] = Form(
    mapping(
      "formId"     -> nonEmptyTextWithError("A form ID is required"),
      "reasonText" -> text.verifying(reasonTextConstraint)
    )(AllowlistEntry.apply)(a => Some(Tuple.fromProductTyped(a)))
  )

  def nonEmptyTextWithError(error: String): Mapping[String] =
    Forms.text verifying Constraint[String]("constraint.required") { o =>
      if (o == null) Invalid(ValidationError(error)) else if (o.trim.isEmpty) Invalid(ValidationError(error)) else Valid
    }

}
