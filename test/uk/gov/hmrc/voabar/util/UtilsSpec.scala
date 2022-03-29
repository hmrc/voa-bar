/*
 * Copyright 2022 HM Revenue & Customs
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

import org.mockito.scalatest.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import uk.gov.hmrc.crypto.{CompositeSymmetricCrypto, Crypted, PlainText}
import uk.gov.hmrc.voabar.models.LoginDetails

import org.apache.commons.codec.binary.Base64
import uk.gov.hmrc.http.HeaderCarrier

class UtilsSpec extends PlaySpec with GuiceOneAppPerSuite with MockitoSugar {

  private val username = "ba0121"
  private val password = "wibble"
  private val goodLogin = LoginDetails(username, password)
  "Utils" must {
    "have decryptPassword method that" must {
      "Decrypt the  encrypted password and return it in plain text" in {
        val cryptoMock = mock[CompositeSymmetricCrypto]
        when(cryptoMock.decrypt(any[Crypted])).thenReturn(PlainText(password))
        val utils = new Utils(cryptoMock)
        val decryptedPassword = utils.decryptPassword(password)
        decryptedPassword mustBe password
      }
    }
    "have generateHeaderCarrier method " must {

      "include some basic authorization in the header" in {
        val cryptoMock = mock[CompositeSymmetricCrypto]
        val utils = new Utils(cryptoMock)

        val hc = utils.generateHeader(goodLogin)

        val encodedAuthHeader = Base64.encodeBase64String(s"${goodLogin.username}:${password}".getBytes("UTF-8"))

        hc.authorization match {
          case Some(s) => hc.authorization.isDefined mustBe true
            s.toString.equals(s"Authorization(Basic ${encodedAuthHeader})") mustBe true
          case _ => assert(false)
        }
      }

      "include some basic authorization in the header for existing header carrier" in {
        val cryptoMock = mock[CompositeSymmetricCrypto]
        val utils = new Utils(cryptoMock)
        val headerCarrier = HeaderCarrier()

        val hc = utils.generateHeader(goodLogin, headerCarrier)

        val encodedAuthHeader = Base64.encodeBase64String(s"${goodLogin.username}:${password}".getBytes("UTF-8"))

        hc.authorization match {
          case Some(s) => hc.authorization.isDefined mustBe true
            s.toString.equals(s"Authorization(Basic ${encodedAuthHeader})") mustBe true
          case _ => assert(false)
        }
      }
    }
  }
}
