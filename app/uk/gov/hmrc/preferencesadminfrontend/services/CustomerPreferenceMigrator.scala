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

import play.api.Logger
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.preferencesadminfrontend.connectors.ChannelPreferencesConnector.StatusUpdate
import uk.gov.hmrc.preferencesadminfrontend.connectors.{ ChannelPreferencesConnector, EntityResolverConnector }
import uk.gov.hmrc.preferencesadminfrontend.model.MTDPMigration
import uk.gov.hmrc.preferencesadminfrontend.model.MTDPMigration.{ ITSAOnlinePreference, MigratingCustomer, SAOnline }

import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class CustomerPreferenceMigrator @Inject() (
  entityResolverConnector: EntityResolverConnector,
  channelPreferencesConnector: ChannelPreferencesConnector
) {
  val logger = Logger(getClass)
  def migrateCustomer(identifier: Identifier, migratingCustomer: MigratingCustomer)(implicit
    headerCarrier: HeaderCarrier,
    executionContext: ExecutionContext
  ): Future[Either[String, Unit]] =
    migratingCustomer match {
      case sa: MTDPMigration.SAOnline               => migrateSAOnline(identifier, sa)
      case itsa: MTDPMigration.ITSAOnlinePreference => migrateITSAOnline(identifier, itsa)
    }

  private def migrateSAOnline(identifier: Identifier, saOnline: SAOnline)(implicit
    headerCarrier: HeaderCarrier,
    executionContext: ExecutionContext
  ): Future[Either[String, Unit]] = {
    logger.debug(s"migrateSAOnline with ${saOnline.entityId.value} and ${identifier.itsaId}")
    for {
      _ <- entityResolverConnector.confirm(saOnline.entityId.value, identifier.itsaId)
      enrolment <- channelPreferencesConnector.updateStatus(
                     StatusUpdate(s"HMRC-MTD-IT~MTDBSA~${identifier.itsaId}", saOnline.isPaperless)
                   )
    } yield enrolment
  }

  private def migrateITSAOnline(identifier: Identifier, itsaOnlinePreference: ITSAOnlinePreference)(implicit
    headerCarrier: HeaderCarrier
  ): Future[Either[String, Unit]] = {
    logger.debug(s"migrateITSAOnline with ${identifier.itsaId} and ${itsaOnlinePreference.isPaperless}")
    channelPreferencesConnector.updateStatus(
      StatusUpdate(s"HMRC-MTD-IT~MTDBSA~${identifier.itsaId}", itsaOnlinePreference.isPaperless)
    )
  }
}
