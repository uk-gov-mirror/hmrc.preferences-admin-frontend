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

import javax.inject.{ Inject, Singleton }
import java.time.{ Instant, ZonedDateTime }
import play.api.Logger
import play.api.http.Status
import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.preferencesadminfrontend.services.model.{ Email, EntityId, TaxIdentifier }
import cats.syntax.either._
import uk.gov.hmrc.http.HttpReads.Implicits.{ readFromJson, readOptionOfNotFound, readRaw }
import uk.gov.hmrc.http.client.HttpClientV2

import java.net.URI
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

object EntityResolverConnector {
  val configKey: String = "entity-resolver"
}

class UnexpectedOptOutResponse(taxId: TaxIdentifier, status: Int, body: String)
    extends RuntimeException(s"Tax ID $taxId returned unexpectd status code $status with body '$body'")

@Singleton
class EntityResolverConnector @Inject() (httpClient: HttpClientV2, val servicesConfig: ServicesConfig) {

  val logger = Logger(getClass)
  implicit val ef: Format[Entity] = Entity.formats
  lazy val serviceUrl: String = servicesConfig.baseUrl(EntityResolverConnector.configKey)

  def sanitize(input: String): String =
    input.replaceAll("[^a-zA-Z0-9-_~]", "")

  def getTaxIdentifiers(
    taxId: TaxIdentifier
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[TaxIdentifier]] = {
    val regime: String = taxId.regime
    val value: String = sanitize(taxId.value)
    val eventualMaybeEntity =
      httpClient
        .get(new URI(s"$serviceUrl/entity-resolver?taxRegime=$regime&taxId=$value").toURL)
        .execute[Option[Entity]]

    eventualMaybeEntity
      .map(
        _.fold(Seq.empty[TaxIdentifier])(entity =>
          Seq(
            entity.sautr.map(TaxIdentifier("sautr", _)),
            entity.nino.map(TaxIdentifier("nino", _)),
            entity.itsaId.map(TaxIdentifier("HMRC-MTD-IT", _))
          ).flatten
        )
      )
      .recover { case ex =>
        logger.error(s"Failed getting tax identifiers for $taxId", ex)
        Seq.empty
      }
  }

  def getTaxIdentifiers(
    preferenceDetails: PreferenceDetails
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[TaxIdentifier]] = {
    val response =
      httpClient
        .get(new URI(s"$serviceUrl/entity-resolver?entityId=${preferenceDetails.entityId.get}").toURL)
        .execute[Option[Entity]]
    response
      .map(
        _.fold(Seq.empty[TaxIdentifier])(entity =>
          Seq(
            entity.sautr.map(TaxIdentifier("sautr", _)),
            entity.nino.map(TaxIdentifier("nino", _)),
            entity.itsaId.map(TaxIdentifier("HMRC-MTD-IT", _))
          ).flatten
        )
      )
      .recover { case ex =>
        logger.error(s"Failed getting tax identifiers for $preferenceDetails", ex)
        Seq.empty
      }
  }

  def getPreferenceDetails(
    taxId: TaxIdentifier
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[PreferenceDetails]] = {
    val regime = taxId.regime
    val value = sanitize(taxId.value)
    httpClient
      .get(new URI(s"$serviceUrl/portal/preferences/$regime/$value").toURL)
      .execute[Option[PreferenceDetails]]
      .recover { case ex =>
        logger.error(s"Failed getting preference details for $taxId", ex)
        None
      }
  }

  def optOut(taxId: TaxIdentifier)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[OptOutResult] = {

    def warnNotOptedOut(status: Int): Unit =
      logger.warn(s"Unable to manually opt-out ${taxId.name} user with id ${taxId.value}. Status: $status")

    httpClient
      .post(new URI(s"$serviceUrl/entity-resolver-admin/manual-opt-out/${taxId.regime}/${taxId.value}").toURL)
      .execute[HttpResponse]
      .flatMap { httpResponse =>
        httpResponse.status match {
          case Status.OK =>
            Future.successful(OptedOut)

          case Status.CONFLICT =>
            warnNotOptedOut(httpResponse.status)
            Future.successful(AlreadyOptedOut)

          case Status.NOT_FOUND =>
            warnNotOptedOut(httpResponse.status)
            Future.successful(PreferenceNotFound)

          case Status.PRECONDITION_FAILED =>
            warnNotOptedOut(httpResponse.status)
            Future.successful(PreferenceNotFound)

          case _ =>
            val exception = new UnexpectedOptOutResponse(taxId, httpResponse.status, httpResponse.body)
            logger.error(exception.getMessage, exception)
            Future.failed(exception)
        }
      }
  }

  def confirm(entityId: String, itsaId: String)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Either[String, Unit]] =
    httpClient
      .post(new URI(s"$serviceUrl/preferences/confirm/$entityId/$itsaId").toURL)
      .transform(_.addHttpHeaders(hc.headers(Seq(HeaderNames.authorisation)): _*))
      .execute[HttpResponse]
      .map { httpResponse =>
        httpResponse.status match {
          case status if Status.isSuccessful(status) => ().asRight
          case other => s"upstream error when confirming ITSA preference, $other ${httpResponse.body}".asLeft
        }
      }
}

sealed trait OptOutResult

case object OptedOut extends OptOutResult

case object AlreadyOptedOut extends OptOutResult {
  val errorCode: String = "AlreadyOptedOut"
}

case object PreferenceNotFound extends OptOutResult {
  val errorCode: String = "PreferenceNotFound"
}

case class Entity(sautr: Option[String], nino: Option[String], itsaId: Option[String])

object Entity {
  implicit val writes: Writes[Entity] = Json.writes[Entity]

  implicit val reads: Reads[Entity] = (
    (JsPath \ "sautr").readNullable[String] and
      (JsPath \ "nino").readNullable[String] and
      (JsPath \ "HMRC-MTD-IT").readNullable[String]
  )((sautr, nino, itsaid) => Entity(sautr, nino, itsaid))

  implicit val formats: Format[Entity] = Format(reads, writes)
}

case class PreferenceDetails(
  genericPaperless: Boolean,
  genericUpdatedAt: Option[ZonedDateTime],
  isPaperless: Option[Boolean],
  email: Option[Email],
  entityId: Option[EntityId] = None,
  eventType: Option[String] = None,
  viaMobileApp: Option[Boolean] = None
)

object PreferenceDetails {
  implicit val localDateRead: Reads[Option[ZonedDateTime]] = new Reads[Option[ZonedDateTime]] {
    override def reads(json: JsValue): JsResult[Option[ZonedDateTime]] =
      json match {
        case JsNumber(dateTime) =>
          Try {
            JsSuccess(Some(ZonedDateTime.from(Instant.ofEpochMilli(dateTime.longValue))))
          }.getOrElse {
            JsError(s"$dateTime is not a valid date")
          }
        case _ => JsError(s"Expected value to be a date, was actually $json")
      }
  }
  implicit val dateFormatDefault: Format[ZonedDateTime] = new Format[ZonedDateTime] {
    override def reads(json: JsValue): JsResult[ZonedDateTime] = Reads.DefaultZonedDateTimeReads.reads(json)
    override def writes(o: ZonedDateTime): JsValue = Writes.ZonedDateTimeEpochMilliWrites.writes(o)
  }
  implicit val reads: Reads[PreferenceDetails] = (
    (JsPath \ "termsAndConditions" \ "generic")
      .readNullable[JsValue]
      .map(_.fold(false)(m => (m \ "accepted").as[Boolean])) and
      (JsPath \ "termsAndConditions" \ "generic")
        .readNullable[JsValue]
        .map(_.fold(None: Option[ZonedDateTime])(m => (m \ "updatedAt").asOpt[ZonedDateTime])) and
      (JsPath \ "termsAndConditions" \ "generic")
        .readNullable[JsValue]
        .map(_.fold(None: Option[Boolean])(m => (m \ "paperless").asOpt[Boolean])) and
      (JsPath \ "email").readNullable[Email] and
      (JsPath \ "entityId").readNullable[EntityId] and
      (JsPath \ "termsAndConditions" \ "generic" \ "eventType").readNullable[String] and
      (JsPath \ "termsAndConditions" \ "generic" \ "isViaMobileApp").readNullable[Boolean]
  )((genericPaperless, genericUpdatedAt, isPaperless, email, entityId, eventType, viaMobileApp) =>
    PreferenceDetails(
      genericPaperless,
      genericUpdatedAt,
      isPaperless,
      email,
      entityId,
      eventType,
      viaMobileApp
    )
  )
}
