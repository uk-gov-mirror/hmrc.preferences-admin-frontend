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

import cats.syntax.either._
import org.mockito.Mockito.when
import org.scalatest.EitherValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.preferencesadminfrontend.connectors.ChannelPreferencesConnector.StatusUpdate
import uk.gov.hmrc.preferencesadminfrontend.connectors.{ ChannelPreferencesConnector, EntityResolverConnector }
import uk.gov.hmrc.preferencesadminfrontend.model.MTDPMigration.{ ITSAOnlinePreference, SAOnline }
import uk.gov.hmrc.preferencesadminfrontend.services.model.EntityId

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CustomerPreferenceMigratorSpec extends PlaySpec with ScalaFutures with EitherValues {

  "migrateCustomer" must {
    "migrate an SAOnline customer" in new Scope {
      when(entityResolverConnector.confirm(saEntityId.value, identifier.itsaId))
        .thenReturn(Future.successful(().asRight))
      when(channelPreferencesConnector.updateStatus(StatusUpdate(s"HMRC-MTD-IT~MTDBSA~${identifier.itsaId}", true)))
        .thenReturn(Future.successful(().asRight))
      customerPreferenceMigrator
        .migrateCustomer(identifier, saOnline)
        .futureValue mustBe Right(())
    }

    "migrate an ITSAOnlinePreference customer" in new Scope {
      when(channelPreferencesConnector.updateStatus(statusUpdate))
        .thenReturn(Future.successful(().asRight))

      customerPreferenceMigrator
        .migrateCustomer(identifier, itsaOnlinePreference)
        .futureValue mustBe Right(())
    }
  }

  trait Scope {
    val saUtr = "SA-I-SAY"
    val itsaId = "I-AM-ITSA"
    val identifier: Identifier = Identifier(itsaId, saUtr)

    val saEntityId: EntityId = EntityId("SA-I-DO")
    val saOnline: SAOnline = SAOnline(saEntityId, true)
    val itsaOnlinePreference: ITSAOnlinePreference = ITSAOnlinePreference(true)
    val statusUpdate: StatusUpdate =
      StatusUpdate(s"HMRC-MTD-IT~MTDBSA~${identifier.itsaId}", itsaOnlinePreference.isPaperless)

    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

    val channelPreferencesConnector: ChannelPreferencesConnector = mock[ChannelPreferencesConnector]
    val entityResolverConnector: EntityResolverConnector = mock[EntityResolverConnector]
    val customerPreferenceMigrator: CustomerPreferenceMigrator = new CustomerPreferenceMigrator(
      entityResolverConnector,
      channelPreferencesConnector
    )
  }
}
