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

package uk.gov.hmrc.preferencesadminfrontend.connectors

import org.apache.pekko.actor.ActorSystem

import javax.inject.{ Inject, Singleton }
import play.api.Configuration
import play.api.http.Status
import play.api.libs.json.{ Format, Json }
import play.api.libs.ws.writeableOf_JsValue
import uk.gov.hmrc.http.*
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.preferencesadminfrontend.controllers.model.{ EmailRequest, Event }

import java.net.URI
import scala.concurrent.{ ExecutionContext, Future }

object PreferencesConnector {
  val configKey = "preferences"
}

@Singleton
class PreferencesConnector @Inject() (
  val httpClient: HttpClientV2,
  val runModeConfiguration: Configuration,
  val servicesConfig: ServicesConfig,
  val actorSystem: ActorSystem
) {

  implicit val ef: Format[Entity] = Entity.formats

  def serviceUrl: String = servicesConfig.baseUrl(PreferencesConnector.configKey)

  def getPreferencesByEmail(
    email: String
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[List[PreferenceDetails]] =
    httpClient
      .post(new URI(s"$serviceUrl/preferences/find-by-email").toURL)
      .withBody(Json.toJson(EmailRequest(email)))
      .execute[List[PreferenceDetails]]
      .recover { case ex @ UpstreamErrorResponse(_, Status.BAD_REQUEST, _, _) =>
        Nil
      }

  def getPreferencesEvents(
    entityId: String
  )(implicit headerCarrier: HeaderCarrier, executionContext: ExecutionContext): Future[List[Event]] =
    httpClient
      .get(new URI(s"$serviceUrl/preferences-admin/events/$entityId").toURL)
      .execute[List[Event]]

}
