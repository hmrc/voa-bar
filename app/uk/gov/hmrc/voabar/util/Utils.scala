/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.voabar.util

import org.apache.commons.codec.binary.Base64
import uk.gov.hmrc.crypto.{Crypted, Decrypter, Encrypter}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.Authorization
import uk.gov.hmrc.voabar.models.LoginDetails

import javax.inject.{Inject, Singleton}

@Singleton
class Utils @Inject() (crypto: Encrypter with Decrypter) {
  def decryptPassword(password: String): String = crypto.decrypt(Crypted(password)).value

  def generateHeader(loginDetails: LoginDetails): HeaderCarrier = {
    val decryptedPassword = loginDetails.password // TODO - should be encrypted. In next version.
    val encodedAuthHeader = Base64.encodeBase64String(s"${loginDetails.username}:$decryptedPassword".getBytes("UTF-8"))
    HeaderCarrier(authorization = Some(Authorization(s"Basic $encodedAuthHeader")))
  }

  def generateHeader(loginDetails: LoginDetails, headerCarrier: HeaderCarrier): HeaderCarrier = {
    val decryptedPassword = loginDetails.password // TODO - should be encrypted. In next version.
    val encodedAuthHeader = Base64.encodeBase64String(s"${loginDetails.username}:$decryptedPassword".getBytes("UTF-8"))
    headerCarrier.copy(authorization = Some(Authorization(s"Basic $encodedAuthHeader")))
  }

}
