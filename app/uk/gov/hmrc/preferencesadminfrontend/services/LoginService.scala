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

import com.google.common.io.BaseEncoding
import com.typesafe.config.ConfigException.Missing

import javax.inject.Inject
import play.api.Configuration
import uk.gov.hmrc.preferencesadminfrontend.controllers.Role
import uk.gov.hmrc.preferencesadminfrontend.controllers.model.User

class LoginService @Inject() (loginServiceConfig: LoginServiceConfiguration) {

  def isAuthorised(user: User): Boolean =
    loginServiceConfig.authorisedUsers.exists(u => u.username == user.username && u.password == user.password)

  def hasRequiredRole(user: User, role: Role): Boolean =
    loginServiceConfig.authorisedUsers.exists(u => u.username == user.username && u.roles.contains(role))
}

class LoginServiceConfiguration @Inject() (val configuration: Configuration) {

  def verifyConfiguration(): Unit = if (authorisedUsers.isEmpty) throw new Missing("Property users is empty")

  lazy val authorisedUsers: Seq[User] =
    configuration
      .getOptional[Seq[Configuration]](s"users")
      .getOrElse(throw new Missing("Property users missing"))
      .map { (userConfig: Configuration) =>
        val encodedPwd =
          userConfig.getOptional[String]("password").getOrElse(throw new Missing("Property password missing"))
        val decodedPwd = new String(BaseEncoding.base64().decode(encodedPwd))
        User(
          userConfig.getOptional[String]("username").getOrElse(throw new Missing("Property username missing")),
          decodedPwd,
          userConfig.getOptional[String]("roles").getOrElse("Generic").split(",").map(Role.fromString).toList
        )
      }
}
