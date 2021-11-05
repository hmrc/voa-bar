/*
 * Copyright 2021 HM Revenue & Customs
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

import java.nio.file.Paths
import java.util.UUID
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.http.Status
import play.api.libs.ws.WSClient
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}


class ValidateControllerItSpec extends PlaySpec with GuiceOneServerPerSuite with DefaultAwaitTimeout with FutureAwaits {

  val BA_LOGIN = "BA5090"

  val requestId = "mdtp-request-" + UUID.randomUUID().toString.replaceAll("-", "")

  val path = Paths.get("test/resources/xml/CTValid1.xml")

  val invalidXml = Paths.get("test/resources/xml/CTInvalid1.xml")

  def wsClient = app.injector.instanceOf[WSClient]


  "Validate controller" should {
    "validate correct xml" in {
      val url = s"http://localhost:${port}/voa-bar/validate-upload/${BA_LOGIN}"

      val response = await(wsClient.url(url)
        .addHttpHeaders("X-Request-ID" -> requestId)
        .put(path.toFile))

      Console.println(response.body)

      response.status mustBe(Status.OK)
    }

    "validate incorrect XML" in {
      val url = s"http://localhost:${port}/voa-bar/validate-upload/7777"

      val response = await(wsClient.url(url)
        .addHttpHeaders("X-Request-ID" -> requestId)
        .put(invalidXml.toFile))

      Console.println(response.body)

      response.status mustBe(Status.OK)


    }
  }



}
