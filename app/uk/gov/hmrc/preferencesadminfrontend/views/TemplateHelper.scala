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

package uk.gov.hmrc.preferencesadminfrontend.views

import play.api.data.Form
import play.api.mvc.Session
import play.twirl.api.Html
import uk.gov.hmrc.preferencesadminfrontend.model.{ GmcBatch, GmcBatchApproval }

object TemplateHelper {

  // format: off
  def getPreviewButton(name: String, value: String, batchList: Seq[GmcBatch], batchId: String, classNames: String = "govuk-button"): Html =
    batchList.find(b => b.batchId == batchId) match {
      case Some(batch) =>
        Html(<input type="hidden" name="batchId" value={batch.batchId}/>
          <input type="hidden" name="formId" value={batch.formId}/>
          <input type="hidden" name="issueDate" value={batch.issueDate}/>
          <input type="hidden" name="templateId" value={batch.templateId}/>
          <button name={name} data-module="govuk-button" class={classNames}>{value}</button>.mkString)
      case None => Html(<fieldset/>.mkString)
    }
  // format: on

  def showFormIds(gmcBatchApproval: Form[GmcBatchApproval]): List[String] =
    gmcBatchApproval("formId").value.map(_.split(",").toList).getOrElse(Nil)

  def validateRole(session: Session, role: String): Boolean = session.get(role).getOrElse("false").toBoolean
}
