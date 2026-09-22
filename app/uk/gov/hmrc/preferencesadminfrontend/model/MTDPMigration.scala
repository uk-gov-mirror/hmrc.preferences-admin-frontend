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

import uk.gov.hmrc.preferencesadminfrontend.services.model.EntityId

object MTDPMigration {
  sealed trait CustomerType

  sealed trait MigratingCustomer extends CustomerType
  sealed trait NonMigratingCustomer extends CustomerType

  case class SAOnline(entityId: EntityId, isPaperless: Boolean) extends MigratingCustomer
  case class ITSAOnlinePreference(isPaperless: Boolean) extends MigratingCustomer

  case object NoDigitalFootprint extends NonMigratingCustomer
  case object ITSAOnlineNoPreference extends NonMigratingCustomer
  case object SAandITSA extends NonMigratingCustomer
}
