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

import cats.data.EitherT
import cats.syntax.either._
import cats.syntax.option._
import play.api.Logger
import play.api.http.Status._
import uk.gov.hmrc.http.{ HeaderCarrier, HttpResponse }
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.preferencesadminfrontend.model.{ PrincipalUserIds, UserState }
import uk.gov.hmrc.preferencesadminfrontend.services.model.TaxIdentifier
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2

import java.net.URI
import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

object EnrolmentStoreConnector {
  val configKey: String = "enrolment-store"
}

@Singleton
class EnrolmentStoreConnector @Inject() (httpClient: HttpClientV2, val servicesConfig: ServicesConfig)(implicit
  ec: ExecutionContext
) {

  val logger = Logger(getClass)
  lazy val serviceUrl: String = servicesConfig.baseUrl(EnrolmentStoreConnector.configKey)

  def getUserIds(taxIdentifier: TaxIdentifier)(implicit hc: HeaderCarrier): Future[Either[String, List[String]]] =
    (for {
      id <- EitherT.fromEither[Future](resolveId(taxIdentifier))
      response <-
        EitherT(
          httpClient
            .get(
              new URI(s"$serviceUrl/enrolment-store-proxy/enrolment-store/enrolments/$id/users?type=principal").toURL
            )
            .execute[HttpResponse]
            .map(handleGetUserIdsResponse)
        )

    } yield response).value

  def getUserState(principalUserId: String, saUtr: TaxIdentifier)(implicit
    hc: HeaderCarrier
  ): Future[Either[String, Option[UserState]]] =
    (for {
      id <- EitherT.fromEither[Future](resolveId(saUtr))
      response <- EitherT(
                    httpClient
                      .get(
                        new URI(
                          s"$serviceUrl/enrolment-store-proxy/enrolment-store/users/$principalUserId/enrolments/$id"
                        ).toURL
                      )
                      .execute[HttpResponse]
                      .map(handleCheckEnrolmentsResponse)
                  )
    } yield response).value

  private def handleGetUserIdsResponse(httpResponse: HttpResponse): Either[String, List[String]] =
    httpResponse.status match {
      case OK         => httpResponse.json.as[PrincipalUserIds].principalUserIds.asRight
      case NO_CONTENT => List.empty[String].asRight
      case other =>
        logger.warn(s"handleGetUserIdsResponseError ${httpResponse.body}")
        s"upstream error when getting principals, $other ${httpResponse.body}".asLeft
    }

  private def handleCheckEnrolmentsResponse(httpResponse: HttpResponse): Either[String, Option[UserState]] =
    httpResponse.status match {
      case OK        => httpResponse.json.as[UserState].some.asRight
      case NOT_FOUND => none.asRight
      case other =>
        logger.warn(s"handleCheckEnrolmentsResponseError ${httpResponse.body}")
        s"upstream error when checking enrolment state, $other ${httpResponse.body}".asLeft
    }

  def resolveId(taxIdentifier: TaxIdentifier): Either[String, String] =
    taxIdentifier.name match {
      case "sautr" => s"IR-SA~UTR~${taxIdentifier.value}".asRight
      case "itsa"  => s"HMRC-MTD-IT~MTDITID~${taxIdentifier.value}".asRight
      case _       => s"unknown tax identifier: ${taxIdentifier.name}, ${taxIdentifier.value}".asLeft
    }
}
