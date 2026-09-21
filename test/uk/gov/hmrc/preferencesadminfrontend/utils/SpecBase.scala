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

package uk.gov.hmrc.preferencesadminfrontend.utils

import org.mockito.ArgumentMatcher
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.i18n.Lang
import play.api.mvc._
import play.api.test.Helpers.{ stubBodyParser, stubControllerComponents, stubLangs, stubMessagesApi }
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.MergedDataEvent
import uk.gov.hmrc.preferencesadminfrontend.connectors.{ EntityResolverConnector, MessageConnector, PreferencesConnector }
import uk.gov.hmrc.preferencesadminfrontend.controllers.model.User

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global

trait SpecBase {

  implicit val user: User = User("me", "mySecretPassword")
  val messagesActionBuilder: MessagesActionBuilder =
    new DefaultMessagesActionBuilderImpl(stubBodyParser[AnyContent](), stubMessagesApi())
  val cc: ControllerComponents = stubControllerComponents()

  implicit val stubbedMCC: MessagesControllerComponents = DefaultMessagesControllerComponents(
    messagesActionBuilder,
    DefaultActionBuilder(stubBodyParser[AnyContent]()),
    cc.parsers,
    stubMessagesApi(
      messages = Map(
        "en" ->
          Map("error.preference_not_found" -> "No paperless preference found for that identifier.")
      )
    ),
    stubLangs(Seq(Lang("en"))),
    cc.fileMimeTypes,
    ExecutionContext.global
  )

  val auditConnectorMock: AuditConnector = mock[AuditConnector]
  val entityResolverConnectorMock: EntityResolverConnector = mock[EntityResolverConnector]
  val preferencesConnectorMock: PreferencesConnector = mock[PreferencesConnector]
  val messageConnectorMock: MessageConnector = mock[MessageConnector]

  def isSimilar(expected: MergedDataEvent): ArgumentMatcher[MergedDataEvent] =
    new ArgumentMatcher[MergedDataEvent]() {
      def matches(t: MergedDataEvent): Boolean =
        t.auditSource == expected.auditSource &&
          t.auditType == expected.auditType &&
          t.request.tags == expected.request.tags &&
          t.request.detail == expected.request.detail &&
          t.response.tags == expected.response.tags &&
          t.response.detail == expected.response.detail
    }
}
