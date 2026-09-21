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

import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.{ Constraint, Invalid, Valid }
import play.api.libs.json.{ Json, OFormat }

case class GmcBatchApproval(
  batchId: String,
  formId: String,
  issueDate: String,
  templateId: String,
  reasonText: String
)

object GmcBatchApproval {

  val reasonTextConstraint: Constraint[String] = Constraint("constraints.reasonText") { reasonText =>
    if (reasonText.isEmpty) {
      Invalid("A reason is required")
    } else if (reasonText.matches("[a-zA-Z0-9\\s\\-\\.,]+")) {
      Valid
    } else {
      Invalid("Invalid characters entered")
    }
  }

  implicit val format: OFormat[GmcBatchApproval] = Json.format[GmcBatchApproval]

  def apply(): Form[GmcBatchApproval] = Form(
    mapping(
      "batchId"    -> nonEmptyText,
      "formId"     -> text,
      "issueDate"  -> text,
      "templateId" -> text,
      "reasonText" -> text.verifying(reasonTextConstraint)
    )(GmcBatchApproval.apply)(g => Some(Tuple.fromProductTyped(g)))
  )

  def apply(gmcBatch: GmcBatch, reason: String): GmcBatchApproval = GmcBatchApproval(
    gmcBatch.batchId,
    gmcBatch.formId,
    gmcBatch.issueDate,
    gmcBatch.templateId,
    reason
  )
}
