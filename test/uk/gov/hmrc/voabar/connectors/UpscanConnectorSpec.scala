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

package uk.gov.hmrc.voabar.connectors

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, equalTo, get, getRequestedFor, urlEqualTo, verify}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import org.scalatest.{BeforeAndAfterEach, EitherValues}
import org.scalatestplus.play.PlaySpec
import play.api.test.{DefaultAwaitTimeout, FutureAwaits, WsTestClient}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.RequestId

import scala.concurrent.ExecutionContext.Implicits.global

class UpscanConnectorSpec extends PlaySpec with FutureAwaits with DefaultAwaitTimeout with EitherValues with BeforeAndAfterEach {

  var wireMockServer: WireMockServer = null

  "upscan connector" should {
    "Include requestId" in {
      WsTestClient.withClient { client =>
        val connector = new DefaultUpscanConnector(client)
        implicit val hc = HeaderCarrier(requestId = Option(RequestId("this-is-request-id")))
        val response = await(connector.downloadReport(url))
        response mustBe('right)
        wireMockServer.verify(
          getRequestedFor(urlEqualTo("/upscan/submission.xml"))
            .withHeader(uk.gov.hmrc.http.HeaderNames.xRequestId, equalTo("this-is-request-id"))
        )
      }
    }
  }

  def url = {
    s"http://localhost:${wireMockServer.port()}/upscan/submission.xml"
  }

  override protected def beforeEach(): Unit = {
    wireMockServer = new WireMockServer(options().dynamicPort())
    wireMockServer.start()
    wireMockServer.stubFor(
      get(urlEqualTo("/upscan/submission.xml"))
        .willReturn(
          aResponse().withStatus(200)
            .withBody("""<root>test</root>""")
        )
    )
  }

  override protected def afterEach(): Unit = {
    wireMockServer.stop()
  }
}
