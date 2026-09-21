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

import javax.inject.{ Inject, Singleton }
import java.time.Instant
import play.api.Configuration
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.{ DataCall, MergedDataEvent }
import uk.gov.hmrc.play.bootstrap.config.AppName
import uk.gov.hmrc.preferencesadminfrontend.connectors.*
import uk.gov.hmrc.preferencesadminfrontend.controllers.model.{ Event, User }
import uk.gov.hmrc.preferencesadminfrontend.services.model.{ EntityId, PrefRoute, Preference, TaxIdentifier }

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class SearchService @Inject() (
  entityResolverConnector: EntityResolverConnector,
  preferencesConnector: PreferencesConnector,
  auditConnector: AuditConnector,
  config: Configuration
) {

  def searchPreference(
    taxId: TaxIdentifier
  )(implicit user: User, hc: HeaderCarrier, ec: ExecutionContext): Future[List[Preference]] = {
    val preferences = if (taxId.name.equals("email")) getPreferencesByEmail(taxId) else getPreference(taxId)
    preferences.map(preference =>
      auditConnector.sendMergedEvent(createSearchEvent(user.username, taxId, preference.headOption))
    )
    preferences
  }

  def searchPreferences(
    taxIds: String
  )(implicit user: User, hc: HeaderCarrier, ec: ExecutionContext): Future[List[(String, String)]] = {
    val ninosToSearch = taxIds.split(",").map(nino => TaxIdentifier("nino", nino.trim)).toList
    Future.traverse(ninosToSearch)(taxId =>
      getPreference(taxId).map(_.flatMap(_.email.map(_.address))).map(value => (taxId.value, value.mkString(",")))
    )
  }

  def buildPreference(
    details: PreferenceDetails,
    taxIdentifiers: Seq[TaxIdentifier],
    events: List[Event]
  ): Preference =
    Preference(
      details.entityId,
      details.genericPaperless,
      details.genericUpdatedAt,
      details.email,
      taxIdentifiers,
      details.eventType.getOrElse(""),
      events,
      PrefRoute.from(details.viaMobileApp.getOrElse(false))
    )

  def getPreferencesByEmail(
    taxId: TaxIdentifier
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[List[Preference]] = {
    val preferences = for {
      preferenceDetails <- preferencesConnector.getPreferencesByEmail(taxId.value)
    } yield preferenceDetails.map { details =>
      for {
        taxIdentifiers <- entityResolverConnector.getTaxIdentifiers(details)
        events         <- getEvents(details.entityId)
      } yield buildPreference(details, taxIdentifiers, events)
    }
    preferences.flatMap(Future.sequence(_)).recover { case _ => Nil }
  }

  def getPreference(
    taxId: TaxIdentifier
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[List[Preference]] = {
    val preferenceDetail = for {
      preferenceDetail <- entityResolverConnector.getPreferenceDetails(taxId)
      taxIdentifiers   <- entityResolverConnector.getTaxIdentifiers(taxId)
      events           <- getEvents(preferenceDetail.flatMap(_.entityId))
    } yield preferenceDetail.map(details => buildPreference(details, taxIdentifiers, events))

    preferenceDetail.map {
      case Some(preference) => List(preference)
      case None             => Nil
    }
  }

  def getEvents(entityId: Option[EntityId])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[List[Event]] =
    entityId match {
      case Some(id) => preferencesConnector.getPreferencesEvents(id.value)
      case None     => Future.successful(List.empty[Event])
    }

  def optOut(taxId: TaxIdentifier, reason: String)(implicit
    user: User,
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[OptOutResult] =
    for {
      originalPreference <- getPreference(taxId)
      optoutResult       <- entityResolverConnector.optOut(taxId)
      newPreference      <- getPreference(taxId)
    } yield {
      auditConnector.sendMergedEvent(
        createOptOutEvent(
          user.username,
          taxId,
          originalPreference.headOption,
          newPreference.headOption,
          optoutResult,
          reason
        )
      )
      optoutResult
    }

  def createOptOutEvent(
    username: String,
    taxIdentifier: TaxIdentifier,
    originalPreference: Option[Preference],
    newPreference: Option[Preference],
    optOutResult: OptOutResult,
    reason: String
  ): MergedDataEvent = {

    val reasonOfFailureJson = optOutResult match {
      case OptedOut           => "Done"
      case AlreadyOptedOut    => "Preference already opted out"
      case PreferenceNotFound => "Preference not found"
    }

    val details: Map[String, String] = Map(
      "user"               -> username,
      "query"              -> Json.toJson(taxIdentifier).toString,
      "optOutReason"       -> reason,
      "originalPreference" -> originalPreference.fold("Not found")(p => Json.toJson(p).toString),
      "newPreference"      -> newPreference.fold("Not found")(p => Json.toJson(p).toString),
      "reasonOfFailure"    -> reasonOfFailureJson
    )

    MergedDataEvent(
      auditSource = AppName.fromConfiguration(config),
      auditType = if (optOutResult == OptedOut) "TxSucceeded" else "TxFailed",
      request = DataCall(
        tags = Map("transactionName" -> "Manual opt out from paperless"),
        detail = details + ("DataCallType" -> "request"),
        generatedAt = Instant.now()
      ),
      response = DataCall(
        tags = Map("transactionName" -> "Manual opt out from paperless"),
        detail = details + ("DataCallType" -> "response"),
        generatedAt = Instant.now()
      )
    )
  }

  def createSearchEvent(
    username: String,
    taxIdentifier: TaxIdentifier,
    preference: Option[Preference]
  ): MergedDataEvent = {

    val details: Map[String, String] = Map(
      "user"       -> username,
      "query"      -> Json.toJson(taxIdentifier).toString,
      "result"     -> preference.fold("Not found")(_ => "Found"),
      "preference" -> preference.fold("Not found")(p => Json.toJson(p).toString)
    )

    MergedDataEvent(
      auditSource = AppName.fromConfiguration(config),
      auditType = "TxSucceeded",
      request = DataCall(
        tags = Map("transactionName" -> "Paperless opt out search"),
        detail = details + ("DataCallType" -> "request"),
        generatedAt = Instant.now()
      ),
      response = DataCall(
        tags = Map("transactionName" -> "Paperless opt out search"),
        detail = details + ("DataCallType" -> "response"),
        generatedAt = Instant.now()
      )
    )
  }
}
