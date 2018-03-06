/*
 * Copyright 2018 HM Revenue & Customs
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

import org.mockito.Matchers._
import org.mockito.Mockito.{verify, when}
import org.scalatestplus.play.PlaySpec
import org.scalatest.mockito.MockitoSugar
import play.api.test.FakeRequest
import uk.gov.hmrc.voabar.connectors.LegacyConnector
import play.api.libs.json.Json
import uk.gov.hmrc.voabar.models.LoginDetails
import play.api.test.Helpers.{status, _}
import play.api.test._

import scala.concurrent.Future
import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext

class LoginControllerSpec extends PlaySpec with MockitoSugar {
  val fakeRequest = FakeRequest("GET", "/")
  def fakeRequestWithJson(jsonStr: String) = {
    val json = Json.parse(jsonStr)
    FakeRequest("POST", "").withHeaders("Content-Type" -> "application/json", "BA-Code" -> "1234").withJsonBody(json)
  }

  val mockLegacyConnector = mock[LegacyConnector]
  when (mockLegacyConnector.validate(any[LoginDetails])(any[ExecutionContext])) thenReturn Future.successful(Success(200))

  val mockLegacyConnectorFailed = mock[LegacyConnector]
  when (mockLegacyConnectorFailed.validate(any[LoginDetails])(any[ExecutionContext])) thenReturn
    Future.successful(Failure(new RuntimeException("Received exception from upstream service")))

  val goodJson = """{"username": "ba0121", "password":"xxxdyyy"}"""
  val wrongJson = """{"usernaem": "ba0121", "passwodr":"xxxdyyy"}"""

  "Given some Json representing a Login with an enquiry, the verify login method creates a Right(loginDetails)" in {
    val controller = new LoginController(mockLegacyConnector)
    val result = controller.verifyLogin(Some(Json.parse(goodJson)))

    result.isRight mustBe true
    result.right.get mustBe LoginDetails("ba0121", "xxxdyyy")
  }

  "return 200 for a POST carrying login detrails" in {
    val result = new LoginController(mockLegacyConnector).login()(fakeRequestWithJson(goodJson))
    status(result) mustBe OK
  }

  "return 400 (badrequest) when given no json" in {
    val fakeRequest = FakeRequest("POST", "").withHeaders("Content-Type" -> "application/json")
    val result = new LoginController(mockLegacyConnector).login()(fakeRequest)
    status(result) mustBe BAD_REQUEST
  }

  "return 400 (badrequest) when given garbled json" in {
    val fakeRequest = FakeRequest("POST", "").withHeaders("Content-Type" -> "application/json").withTextBody("{")
    val result = new LoginController(mockLegacyConnector).login()(fakeRequest)
    status(result) mustBe BAD_REQUEST
  }

  "Given some wrong Json format, the createContact method returns a Left(Unable to parse)" in {
    val controller = new LoginController(mockLegacyConnector)
    val result = controller.verifyLogin(Some(Json.parse(wrongJson)))

    result.isLeft mustBe true
  }

  "return a Failure when the backend service call fails" in {
    val controller = new LoginController(mockLegacyConnectorFailed)

    intercept[Exception] {
      val result = controller.login()(fakeRequestWithJson(goodJson))
      status(result) mustBe INTERNAL_SERVER_ERROR
    }
  }


}
