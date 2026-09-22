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
import cats.syntax.option._
import org.mockito.Mockito.when
import org.scalatest.EitherValues
import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.preferencesadminfrontend.connectors.{ EnrolmentStoreConnector, EntityResolverConnector, PreferenceDetails }
import uk.gov.hmrc.preferencesadminfrontend.model.MTDPMigration.{ ITSAOnlineNoPreference, ITSAOnlinePreference, NoDigitalFootprint, SAOnline, SAandITSA }
import uk.gov.hmrc.preferencesadminfrontend.model.UserState.Activated
import uk.gov.hmrc.preferencesadminfrontend.model.UserState
import uk.gov.hmrc.preferencesadminfrontend.services.model.{ EntityId, TaxIdentifier }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CustomerMigrationResolverSpec extends PlaySpec with ScalaFutures with EitherValues with IntegrationPatience {

  "resolveCustomerType" must {
    "Activated SA enrolment with a preference and no ITSA enrolment => a SAOnline" in new Scope {
      when(enrolmentStoreConnector.getUserIds(saTaxId))
        .thenReturn(Future.successful(List(saPrincipalUserId).asRight))

      when(enrolmentStoreConnector.getUserIds(itsaTaxId))
        .thenReturn(Future.successful(List.empty.asRight))

      when(enrolmentStoreConnector.getUserState(saPrincipalUserId, saTaxId))
        .thenReturn(Future.successful(userState.some.asRight))

      when(entityResolverConnector.getPreferenceDetails(saTaxId))
        .thenReturn(Future.successful(saPreferenceDetails.some))

      customerMigrationResolver
        .resolveCustomerType(identifier)(headerCarrier)
        .futureValue mustBe Right(SAOnline(saEntityId, true))
    }

    "Activated SA enrolment with a preference and an ITSA enrolment with a preference => SAandITSA" in new Scope {
      when(enrolmentStoreConnector.getUserIds(saTaxId))
        .thenReturn(Future.successful(List(saPrincipalUserId).asRight))

      when(enrolmentStoreConnector.getUserIds(itsaTaxId))
        .thenReturn(Future.successful(List(itsaPrincipalUserId).asRight))

      when(enrolmentStoreConnector.getUserState(saPrincipalUserId, saTaxId))
        .thenReturn(Future.successful(userState.some.asRight))

      when(entityResolverConnector.getPreferenceDetails(saTaxId))
        .thenReturn(Future.successful(saPreferenceDetails.some))

      when(entityResolverConnector.getPreferenceDetails(itsaTaxId))
        .thenReturn(Future.successful(itsaPreferenceDetails.some))

      customerMigrationResolver
        .resolveCustomerType(identifier)(headerCarrier)
        .futureValue mustBe Right(SAandITSA)
    }

    "Activated SA enrolment with no preference and an ITSA enrolment with a preference => ITSAOnlinePreference" in new Scope {
      when(enrolmentStoreConnector.getUserIds(saTaxId))
        .thenReturn(Future.successful(List(saPrincipalUserId).asRight))

      when(enrolmentStoreConnector.getUserIds(itsaTaxId))
        .thenReturn(Future.successful(List(itsaPrincipalUserId).asRight))

      when(enrolmentStoreConnector.getUserState(saPrincipalUserId, saTaxId))
        .thenReturn(Future.successful(userState.some.asRight))

      when(entityResolverConnector.getPreferenceDetails(saTaxId))
        .thenReturn(Future.successful(none))

      when(entityResolverConnector.getPreferenceDetails(itsaTaxId))
        .thenReturn(Future.successful(itsaPreferenceDetails.some))

      customerMigrationResolver
        .resolveCustomerType(identifier)(headerCarrier)
        .futureValue mustBe Right(ITSAOnlinePreference(true))
    }

    "Activated SA enrolment with a preference and an ITSA enrolment with no preference => ITSAOnlineNoPreference" in new Scope {
      when(enrolmentStoreConnector.getUserIds(saTaxId))
        .thenReturn(Future.successful(List(saPrincipalUserId).asRight))

      when(enrolmentStoreConnector.getUserIds(itsaTaxId))
        .thenReturn(Future.successful(List(itsaPrincipalUserId).asRight))

      when(enrolmentStoreConnector.getUserState(saPrincipalUserId, saTaxId))
        .thenReturn(Future.successful(userState.some.asRight))

      when(entityResolverConnector.getPreferenceDetails(saTaxId))
        .thenReturn(Future.successful(saPreferenceDetails.copy(entityId = none).some))

      when(entityResolverConnector.getPreferenceDetails(itsaTaxId))
        .thenReturn(Future.successful(itsaPreferenceDetails.copy(isPaperless = none).some))

      customerMigrationResolver
        .resolveCustomerType(identifier)(headerCarrier)
        .futureValue mustBe Right(ITSAOnlineNoPreference)
    }

    "Inactive SA enrolment with no preference and an ITSA enrolment with no preference => ITSAOnlineNoPreference" in new Scope {
      when(enrolmentStoreConnector.getUserIds(saTaxId))
        .thenReturn(Future.successful(List(saPrincipalUserId).asRight))

      when(enrolmentStoreConnector.getUserIds(itsaTaxId))
        .thenReturn(Future.successful(List(itsaPrincipalUserId).asRight))

      when(enrolmentStoreConnector.getUserState(saPrincipalUserId, saTaxId))
        .thenReturn(Future.successful(userState.copy(state = "NotYetActivated").some.asRight))

      when(entityResolverConnector.getPreferenceDetails(itsaTaxId))
        .thenReturn(Future.successful(itsaPreferenceDetails.copy(isPaperless = none).some))

      customerMigrationResolver
        .resolveCustomerType(identifier)(headerCarrier)
        .futureValue mustBe Right(ITSAOnlineNoPreference)
    }

    "Inactive SA enrolment with no preference and no ITSA enrolment => NoDigitalFootprint" in new Scope {
      when(enrolmentStoreConnector.getUserIds(saTaxId))
        .thenReturn(Future.successful(List(saPrincipalUserId).asRight))

      when(enrolmentStoreConnector.getUserIds(itsaTaxId))
        .thenReturn(Future.successful(List.empty.asRight))

      when(enrolmentStoreConnector.getUserState(saPrincipalUserId, saTaxId))
        .thenReturn(Future.successful(userState.copy(state = "NotYetActivated").some.asRight))

      customerMigrationResolver
        .resolveCustomerType(identifier)(headerCarrier)
        .futureValue mustBe Right(NoDigitalFootprint)
    }
  }

  trait Scope {
    val saUtr = "SA-I-SAY"
    val itsaId = "I-AM-ITSA"
    val identifier: Identifier = Identifier(itsaId, saUtr)

    val saPrincipalUserId = "MY-ID-SA"
    val itsaPrincipalUserId = "MY-ID-ITSA"

    val userState: UserState = UserState(Activated)

    val saTaxId: TaxIdentifier = TaxIdentifier("sautr", saUtr)
    val saEntityId: EntityId = EntityId("SA-I-DO")
    val itsaTaxId: TaxIdentifier = TaxIdentifier("itsa", itsaId)
    val itsaEntityId: EntityId = EntityId("ITSA-I-DO")

    val saPreferenceDetails: PreferenceDetails = PreferenceDetails(
      genericPaperless = false,
      genericUpdatedAt = none,
      isPaperless = true.some,
      email = none,
      entityId = saEntityId.some
    )

    val itsaPreferenceDetails: PreferenceDetails = PreferenceDetails(
      genericPaperless = false,
      genericUpdatedAt = none,
      isPaperless = true.some,
      email = none,
      entityId = itsaEntityId.some
    )

    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

    val enrolmentStoreConnector: EnrolmentStoreConnector = mock[EnrolmentStoreConnector]
    val entityResolverConnector: EntityResolverConnector = mock[EntityResolverConnector]
    val customerMigrationResolver: CustomerMigrationResolver = new CustomerMigrationResolver(
      enrolmentStoreConnector,
      entityResolverConnector
    )
  }
}
