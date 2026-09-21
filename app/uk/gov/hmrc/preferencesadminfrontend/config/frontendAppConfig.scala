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

package uk.gov.hmrc.preferencesadminfrontend.config

import javax.inject.Inject
import play.api.{ Configuration, Environment }
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

trait AppConfig {
  val analyticsToken: String
  val analyticsHost: String
  val reportAProblemPartialUrl: String
  val reportAProblemNonJSUrl: String
  val featureFlag: String
  val validFormIds: Seq[String]
}

class FrontendAppConfig @Inject() (
  val configuration: Configuration,
  val environment: Environment,
  val serviceConfig: ServicesConfig
) extends AppConfig {

  private def loadConfig(key: String) =
    configuration.getOptional[String](key).getOrElse(throw new Exception(s"Missing configuration key: $key"))

  private val contactHost = configuration.getOptional[String](s"contact-frontend.host").getOrElse("")
  private val contactFormServiceIdentifier = "MyService"

  override val featureFlag: String = loadConfig("featureFlag.migration")
  override val analyticsToken: String = loadConfig(s"google-analytics.token")
  override val analyticsHost: String = loadConfig(s"google-analytics.host")
  override val reportAProblemPartialUrl =
    s"$contactHost/contact/problem_reports_ajax?service=$contactFormServiceIdentifier"
  override val reportAProblemNonJSUrl =
    s"$contactHost/contact/problem_reports_nonjs?service=$contactFormServiceIdentifier"

  override val validFormIds: Seq[String] =
    configuration.getOptional[Seq[String]]("formIds").getOrElse(FormIds.configList).map(_.toUpperCase)
}
