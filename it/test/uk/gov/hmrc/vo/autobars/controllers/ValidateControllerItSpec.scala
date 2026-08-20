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

import play.api.test.Helpers.*
import uk.gov.hmrc.vo.integration.test.BaseServerSpec

import java.nio.file.Paths
import java.util.UUID

class ValidateControllerItSpec extends BaseServerSpec:

  private val BA_LOGIN   = "BA5090"
  private val requestId  = "mdtp-request-" + UUID.randomUUID.toString.replaceAll("-", "")
  private val path       = Paths.get("test/resources/xml/CTValid1.xml")
  private val invalidXml = Paths.get("test/resources/xml/CTInvalid1.xml")

  "Validate controller" should {
    "validate correct xml" in {
      val url = s"$serverBaseUrl/voa-bar/validate-upload/$BA_LOGIN"

      val response = wsClient.url(url)
        .addHttpHeaders("X-Request-ID" -> requestId)
        .put(path.toFile).futureValue

      Console.println(response.body)

      response.status shouldBe OK
    }

    "validate incorrect XML" in {
      val url = s"$serverBaseUrl/voa-bar/validate-upload/7777"

      val response = wsClient.url(url)
        .addHttpHeaders("X-Request-ID" -> requestId)
        .put(invalidXml.toFile).futureValue

      Console.println(response.body)

      response.status shouldBe OK
    }
  }
