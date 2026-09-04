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

package uk.gov.hmrc.vo.autobars.controllers

import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.crypto.{ApplicationCrypto, PlainText}
import uk.gov.hmrc.vo.autobars.connectors.{VOBarAuditConnector, VOEbarsConnector}
import uk.gov.hmrc.vo.autobars.models.LoginDetails
import uk.gov.hmrc.vo.unit.test.BaseAppSpec

import scala.concurrent.Future
import scala.util.{Failure, Success}

class LoginControllerSpec extends BaseAppSpec:

  private def fakeRequestWithJson(jsonStr: String) =
    val json = Json.parse(jsonStr)
    FakeRequest("POST", "").withHeaders("Content-Type" -> "application/json", "BA-Code" -> "1234").withJsonBody(json)

  private val mockVOEbarsConnector = mock[VOEbarsConnector]
  when(mockVOEbarsConnector.validate(any[LoginDetails])).thenReturn(Future.successful(Success(OK)))

  private val mockVOEbarsConnectorFailed = mock[VOEbarsConnector]
  when(mockVOEbarsConnectorFailed.validate(any[LoginDetails])).thenReturn(
    Future.successful(Failure(RuntimeException("Received exception from upstream service")))
  )

  private val mockAudit = mock[VOBarAuditConnector]

  private val applicationCrypto = app.injector.instanceOf[ApplicationCrypto]
  private val encryptedPassword = applicationCrypto.JsonCrypto.encrypt(PlainText("xxxdyyy")).value

  private val goodJson  = s"""{"username": "ba0121", "password":"$encryptedPassword"}"""
  private val wrongJson = """{"usernaem": "ba0121", "passwodr":"xxxdyyy"}"""

  private def controller = LoginController(mockVOEbarsConnector, mockAudit, applicationCrypto, stubControllerComponents())

  "LoginController" should {
    "given some Json representing a Login with an enquiry, the verify login method creates a Right(loginDetails)" in {
      val result = controller.verifyLogin(Some(Json.parse(goodJson)))

      result.isRight  shouldBe true
      result.toOption shouldBe Some(LoginDetails("ba0121", "xxxdyyy"))
    }

    "return 200 for a POST carrying login details" in {
      val result = controller.login()(fakeRequestWithJson(goodJson))
      status(result) shouldBe OK
    }

    "return 400 when given no json" in {
      val fakeRequest = FakeRequest("POST", "").withHeaders("Content-Type" -> "application/json")
      val result      = controller.login()(fakeRequest)
      status(result) shouldBe BAD_REQUEST
    }

    "return 400 when given garbled json" in {
      val fakeRequest = FakeRequest("POST", "").withHeaders("Content-Type" -> "application/json").withTextBody("{")
      val result      = controller.login()(fakeRequest)
      status(result) shouldBe BAD_REQUEST
    }

    "given some wrong Json format, the createContact method returns a Left(Unable to parse)" in {
      val result = controller.verifyLogin(Some(Json.parse(wrongJson)))
      result.isLeft shouldBe true
    }

    "return a Failure when the backend service call fails" in
      intercept[Exception] {
        val result = controller.login()(fakeRequestWithJson(goodJson))
        status(result) shouldBe INTERNAL_SERVER_ERROR
      }
  }
