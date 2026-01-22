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

package uk.gov.hmrc.voabar.controllers

import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.Configuration
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.crypto.{ApplicationCrypto, PlainText}
import uk.gov.hmrc.voabar.models.UploadDetails
import uk.gov.hmrc.voabar.services.ReportUploadService
import play.api.test.Helpers.stubControllerComponents

class UploadControllerSpec extends PlaySpec with MockitoSugar {

  private val reportUploadService = mock[ReportUploadService]

  private val configuration = Configuration("json.encryption.key" -> "gvBoGdgzqG1AarzF1LY0zQ==")

  private val crypto = new ApplicationCrypto(configuration.underlying).JsonCrypto

  private val encryptedPassword = crypto.encrypt(PlainText("password")).value

  private val controller = new UploadController(reportUploadService, configuration, stubControllerComponents())

  def fakeRequestWithXML: FakeRequest[UploadDetails] =
    FakeRequest("POST", "/request?reference=1234")
      .withHeaders(
        "BA-Code"  -> "1234",
        "password" -> encryptedPassword
      )
      .withBody(UploadDetails("1234", "url"))

  def fakeRequestWithXMLButNoBACode: FakeRequest[UploadDetails] =
    FakeRequest("POST", "")
      .withBody(UploadDetails("1234", "url"))

  def fakeRequestWithXMLButNoPassword: FakeRequest[UploadDetails] =
    FakeRequest("POST", "")
      .withHeaders(
        "BA-Code" -> "1234"
      )
      .withBody(UploadDetails("1234", "url"))

  "Return status 200 (OK) for a post carrying xml" in {
    val result = controller.upload()(fakeRequestWithXML)
    status(result) mustBe 200
  }

  "A request must contain a Billing Authority Code in the header" in {
    val result = controller.upload()(fakeRequestWithXMLButNoBACode)
    status(result) mustBe UNAUTHORIZED
  }

  "A request must contain a password in the header" in {
    val result = controller.upload()(fakeRequestWithXMLButNoPassword)
    status(result) mustBe UNAUTHORIZED
  }

}
