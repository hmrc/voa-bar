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

import java.nio.file.{Files, Paths, StandardCopyOption}
import java.util.UUID

import org.scalatestplus.play.*
import play.api.libs.Files.SingletonTemporaryFileCreator
import play.api.mvc.*
import play.api.test.*
import play.api.test.Helpers.*
import uk.gov.hmrc.vo.autobars.services.{V1ValidationService, ValidationService}

import scala.concurrent.ExecutionContext.Implicits.global

class ValidateControllerSpec extends PlaySpec with Results:

  val BA_LOGIN = "BA5090"

  private val requestId = "mdtp-request-" + UUID.randomUUID.toString.replaceAll("-", "")

  "Validate controller" should {
    "validate correct xml" in {
      val controller = ValidateController(Helpers.stubControllerComponents(), aSubmissionProcessingService())
      val response   = controller.validate(BA_LOGIN).apply(aSuccessfulRequest)
      status(response) mustBe OK
    }
  }

  private def aSuccessfulRequest =
    val path     = Paths.get("test/resources/xml/CTValid1.xml")
    val tempPath = Files.createTempFile("CTValid1", "xml")

    Files.copy(path, tempPath, StandardCopyOption.REPLACE_EXISTING)

    val tempFile = SingletonTemporaryFileCreator.create(tempPath)
    FakeRequest(POST, "/sss").withBody(tempFile)
      .withHeaders("X-Request-ID" -> requestId)

  private def aSubmissionProcessingService() = V1ValidationService(aValidationService)

  private def aValidationService = ValidationService()
