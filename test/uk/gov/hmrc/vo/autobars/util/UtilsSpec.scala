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

package uk.gov.hmrc.vo.autobars.util

import org.apache.commons.codec.binary.Base64
import uk.gov.hmrc.crypto.{Crypted, Decrypter, Encrypter, PlainText}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.vo.autobars.models.LoginDetails
import uk.gov.hmrc.vo.unit.test.BaseSpec

class UtilsSpec extends BaseSpec:

  private val username  = "ba0121"
  private val password  = "wibble"
  private val goodLogin = LoginDetails(username, password)

  "Utils.decryptPassword" should {
    "Decrypt the  encrypted password and return it in plain text" in {
      val cryptoMock        = mock[Encrypter & Decrypter]
      when(cryptoMock.decrypt(any[Crypted])).thenReturn(PlainText(password))
      val utils             = Utils(cryptoMock)
      val decryptedPassword = utils.decryptPassword(password)
      decryptedPassword shouldBe password
    }
  }

  "Utils.generateHeaderCarrier" should {
    "include some basic authorization in the header" in {
      val cryptoMock = mock[Encrypter & Decrypter]
      val utils      = Utils(cryptoMock)

      val hc = utils.generateHeader(goodLogin)

      val encodedAuthHeader = Base64.encodeBase64String(s"${goodLogin.username}:$password".getBytes("UTF-8"))

      hc.authorization match
        case Some(s) =>
          hc.authorization.isDefined                                    shouldBe true
          s.toString.equals(s"Authorization(Basic $encodedAuthHeader)") shouldBe true
        case _       => assert(false)
    }

    "include some basic authorization in the header for existing header carrier" in {
      val cryptoMock    = mock[Encrypter & Decrypter]
      val utils         = Utils(cryptoMock)
      val headerCarrier = HeaderCarrier()

      val hc = utils.generateHeader(goodLogin, headerCarrier)

      val encodedAuthHeader = Base64.encodeBase64String(s"${goodLogin.username}:$password".getBytes("UTF-8"))

      hc.authorization match
        case Some(s) =>
          hc.authorization.isDefined                                    shouldBe true
          s.toString.equals(s"Authorization(Basic $encodedAuthHeader)") shouldBe true
        case _       => assert(false)
    }
  }
