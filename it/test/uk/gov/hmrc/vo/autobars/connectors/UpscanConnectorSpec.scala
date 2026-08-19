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

package uk.gov.hmrc.vo.autobars.connectors

import com.github.tomakehurst.wiremock.client.WireMock.*
import play.api.test.FutureAwaits
import play.api.test.Helpers.*
import uk.gov.hmrc.http.{HeaderCarrier, HeaderNames, RequestId}
import uk.gov.hmrc.vo.autobars.connectors.DefaultUpscanConnector
import uk.gov.hmrc.vo.integration.test.BaseServerSpec

class UpscanConnectorSpec extends BaseServerSpec with FutureAwaits:

  private val upScanPath = "/upscan/submission.xml"

  private def upScanUrl: String = s"$wireMockBaseUrl$upScanPath"

  "UpScan connector" should {
    "include requestId" in {
      wireMockServer.stubFor(
        get(urlEqualTo(upScanPath))
          .willReturn(
            aResponse().withStatus(OK)
              .withBody("""<root>test</root>""")
          )
      )

      given HeaderCarrier = HeaderCarrier(requestId = Option(RequestId("this-is-request-id")))

      val connector = DefaultUpscanConnector(wsClient)
      val response  = await(connector.downloadReport(upScanUrl))

      response shouldBe Symbol("right")

      wireMockServer.verify(
        getRequestedFor(urlEqualTo(upScanPath))
          .withHeader(HeaderNames.xRequestId, equalTo("this-is-request-id"))
      )
    }
  }
