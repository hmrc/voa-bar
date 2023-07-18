/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.connectors

import java.util.UUID
import com.github.tomakehurst.wiremock.WireMockServer
import ebars.xml.BAreports

import jakarta.xml.bind.JAXBContext
import javax.xml.transform.stream.StreamSource
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Configuration
import play.api.test.Injecting
import services.EbarsValidator
import uk.gov.hmrc.WiremockHelper
import uk.gov.hmrc.crypto.ApplicationCrypto
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.http.HttpClient
import uk.gov.hmrc.voabar.util.Utils
import uk.gov.hmrc.voabar.connectors.DefaultLegacyConnector
import uk.gov.hmrc.voabar.models.EbarsRequests.BAReportRequest

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.language.postfixOps

class DefaultLegacyConnectorItSpec extends PlaySpec with WiremockHelper with GuiceOneAppPerSuite with Injecting {

  def legacyConnector(port: Int) = {

    val config = inject[Configuration]

    val servicesConfig = new ServicesConfig(Configuration("microservice.services.autobars-stubs.port" -> port).withFallback(config))

    new DefaultLegacyConnector(inject[HttpClient], servicesConfig, inject[Utils], inject[ApplicationCrypto])

  }
  implicit def ec = app.injector.instanceOf[ExecutionContext]

  val ebarsValidator = new EbarsValidator()

  val timeout = 1000 milliseconds

  "LegacyConnector" must {
    "send all request as UTF-8 with encoding in mime type" in {

      withWiremockServer { wireMockServer: WireMockServer =>
        import com.github.tomakehurst.wiremock.client.WireMock._
        wireMockServer.stubFor(
          post(urlEqualTo("/autobars-stubs/v2/submit"))
            .willReturn(
              aResponse().withStatus(200)
            )
        )
      }{ (port: Int, wireMockServer: WireMockServer) =>
        import com.github.tomakehurst.wiremock.client.WireMock._
        implicit val hc = HeaderCarrier()

        val jsonString = ebarsValidator.toJson(aBaReport)

        val baReportReques = BAReportRequest(
          UUID.randomUUID().toString,
          jsonString,
          "BA5090",
          "BA5090",
          1
        )

        val result = legacyConnector(port).sendBAReport(baReportReques)

        val httpResult = Await.result(result, timeout)
        httpResult mustBe 200

        wireMockServer.verify(postRequestedFor(urlEqualTo("/autobars-stubs/v2/submit"))
                .withHeader("Content-Type", equalTo("text/plain; charset=UTF-8")))

      }

    }
  }


  def aBaReport: BAreports = {
    val ctx = JAXBContext.newInstance("ebars.xml")
    val unmarshaller = ctx.createUnmarshaller()
    val streamSource = new StreamSource("test/resources/xml/CTValid2.xml")
    unmarshaller.unmarshal( streamSource , classOf[BAreports]).getValue
  }



}
