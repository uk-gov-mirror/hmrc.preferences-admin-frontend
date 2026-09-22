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

import com.google.inject.{ AbstractModule, Provides }
import play.api.Configuration
import uk.gov.hmrc.preferencesadminfrontend.config.{ AppConfig, BulkOptOutsConfig, FrontendAppConfig }

class FrontendModule extends AbstractModule {

  override def configure(): Unit =
    bind(classOf[AppConfig]).to(classOf[FrontendAppConfig]).asEagerSingleton()

  @Provides protected def provideBulkOptOutsConfig(configuration: Configuration): BulkOptOutsConfig = {
    val bulOptOutsConfigObject = configuration.get[Configuration]("bulkOptOuts")

    BulkOptOutsConfig(
      maxUploadEntries = bulOptOutsConfigObject.get("maxUploadEntries"),
      maxOptOutsPerSecond = bulOptOutsConfigObject.get("maxOptOutsPerSecond")
    )
  }
}
