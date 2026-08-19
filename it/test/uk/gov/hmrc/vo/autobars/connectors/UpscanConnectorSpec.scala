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

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import org.scalatest.{BeforeAndAfterEach, EitherValues}
import org.scalatestplus.play.PlaySpec
import play.api.http.Status.OK
import play.api.test.{DefaultAwaitTimeout, FutureAwaits, WsTestClient}
import uk.gov.hmrc.http.{HeaderCarrier, RequestId}
import uk.gov.hmrc.vo.autobars.WiremockHelper
import uk.gov.hmrc.vo.autobars.connectors.DefaultUpscanConnector

import uk.gov.hmrc.http.HeaderNames

import scala.concurrent.ExecutionContext.Implicits.global

class UpscanConnectorSpec extends PlaySpec with WiremockHelper with FutureAwaits with DefaultAwaitTimeout with EitherValues with BeforeAndAfterEach:

  private val upScanPath = "/upscan/submission.xml"

  def url(port: Int): String = s"http://localhost:$port$upScanPath"

  "UpScan connector" should {
    "Include requestId" in
      withWiremockServer { wireMockServer =>
        wireMockServer.stubFor(
          get(urlEqualTo(upScanPath))
            .willReturn(
              aResponse().withStatus(OK)
                .withBody("""<root>test</root>""")
            )
        )
      } { (port: Int, wireMockServer: WireMockServer) =>
        WsTestClient.withClient { client =>
          given HeaderCarrier = HeaderCarrier(requestId = Option(RequestId("this-is-request-id")))

          val connector = DefaultUpscanConnector(client)
          val response  = await(connector.downloadReport(url(port)))
          response mustBe Symbol("right")

          wireMockServer.verify(
            getRequestedFor(urlEqualTo(upScanPath))
              .withHeader(HeaderNames.xRequestId, equalTo("this-is-request-id"))
          )
        }
      }
  }
