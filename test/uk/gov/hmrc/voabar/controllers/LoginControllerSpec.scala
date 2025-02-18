/*
 * Copyright 2024 HM Revenue & Customs
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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.test.FakeRequest
import uk.gov.hmrc.voabar.connectors.{VoaBarAuditConnector, VoaEbarsConnector}
import play.api.libs.json.Json
import uk.gov.hmrc.voabar.models.LoginDetails
import play.api.test.Helpers.*
import uk.gov.hmrc.http.HeaderCarrier
import play.api.test.Helpers.stubControllerComponents
import uk.gov.hmrc.crypto.{ApplicationCrypto, PlainText}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class LoginControllerSpec extends PlaySpec with MockitoSugar with GuiceOneAppPerSuite {
  val fakeRequest = FakeRequest("GET", "/")

  def fakeRequestWithJson(jsonStr: String) = {
    val json = Json.parse(jsonStr)
    FakeRequest("POST", "").withHeaders("Content-Type" -> "application/json", "BA-Code" -> "1234").withJsonBody(json)
  }

  val mockVoaEbarsConnector = mock[VoaEbarsConnector]
  when(mockVoaEbarsConnector.validate(any[LoginDetails])(any[ExecutionContext], any[HeaderCarrier])).thenReturn(Future.successful(Success(OK)))

  val mockVoaEbarsConnectorFailed = mock[VoaEbarsConnector]
  when(mockVoaEbarsConnectorFailed.validate(any[LoginDetails])(any[ExecutionContext], any[HeaderCarrier])).thenReturn(
    Future.successful(Failure(new RuntimeException("Received exception from upstream service")))
  )

  val mockAudit = mock[VoaBarAuditConnector]

  val applicationCrypto = app.injector.instanceOf[ApplicationCrypto]
  val encryptedPassword = applicationCrypto.JsonCrypto.encrypt(PlainText("xxxdyyy")).value

  val goodJson  = s"""{"username": "ba0121", "password":"$encryptedPassword"}"""
  val wrongJson = """{"usernaem": "ba0121", "passwodr":"xxxdyyy"}"""

  private def controller = new LoginController(mockVoaEbarsConnector, mockAudit, applicationCrypto, stubControllerComponents())

  "Given some Json representing a Login with an enquiry, the verify login method creates a Right(loginDetails)" in {
    val result = controller.verifyLogin(Some(Json.parse(goodJson)))

    result.isRight mustBe true
    result.toOption mustBe Some(LoginDetails("ba0121", "xxxdyyy"))
  }

  "return 200 for a POST carrying login details" in {
    val result = controller.login()(fakeRequestWithJson(goodJson))
    status(result) mustBe OK
  }

  "return 400 (badrequest) when given no json" in {
    val fakeRequest = FakeRequest("POST", "").withHeaders("Content-Type" -> "application/json")
    val result      = controller.login()(fakeRequest)
    status(result) mustBe BAD_REQUEST
  }

  "return 400 (badrequest) when given garbled json" in {
    val fakeRequest = FakeRequest("POST", "").withHeaders("Content-Type" -> "application/json").withTextBody("{")
    val result      = controller.login()(fakeRequest)
    status(result) mustBe BAD_REQUEST
  }

  "Given some wrong Json format, the createContact method returns a Left(Unable to parse)" in {
    val result = controller.verifyLogin(Some(Json.parse(wrongJson)))

    result.isLeft mustBe true
  }

  "return a Failure when the backend service call fails" in
    intercept[Exception] {
      val result = controller.login()(fakeRequestWithJson(goodJson))
      status(result) mustBe INTERNAL_SERVER_ERROR
    }

}
