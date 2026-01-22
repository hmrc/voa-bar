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

package uk.gov.hmrc.connectors

import java.util.UUID
import com.github.tomakehurst.wiremock.WireMockServer
import ebars.xml.BAreports
import jakarta.xml.bind.JAXBContext

import javax.xml.transform.stream.StreamSource
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Configuration
import play.api.http.Status.{INTERNAL_SERVER_ERROR, OK, UNAUTHORIZED}
import play.api.test.Injecting
import services.EbarsValidator
import uk.gov.hmrc.WiremockHelper
import uk.gov.hmrc.http.{HeaderCarrier, UnauthorizedException}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.voabar.connectors.{DefaultVoaEbarsConnector, VoaBarAuditConnector, VoaEbarsConnector}
import uk.gov.hmrc.voabar.models.EbarsRequests.BAReportRequest
import uk.gov.hmrc.voabar.services.{EbarsApiError, EbarsClientV2}
import com.github.tomakehurst.wiremock.client.WireMock.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.voabar.models.LoginDetails

import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

class VoaEbarsConnectorItSpec extends PlaySpec with WiremockHelper with GuiceOneAppPerSuite with Injecting {

  def voaEbarsConnector(port: Int): VoaEbarsConnector = {
    val config         = inject[Configuration]
    val servicesConfig = new ServicesConfig(Configuration("microservice.services.voa-ebars.port" -> port).withFallback(config))

    val ebarsClientV2 = new EbarsClientV2(inject[HttpClientV2], servicesConfig)
    new DefaultVoaEbarsConnector(ebarsClientV2, inject[VoaBarAuditConnector])
  }

  implicit def ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]

  val ebarsValidator = new EbarsValidator()

  private val timeout = 1 seconds

  val loginPath         = "/ebars_dmz_pres_ApplicationWeb/Welcome.do"
  val uploadXmlPath     = "/ebars_dmz_pres_ApplicationWeb/uploadXmlSubmission"
  val uploadContentType = "application/x-www-form-urlencoded"

  implicit val hc: HeaderCarrier = HeaderCarrier()

  private val loginDetails = LoginDetails("BA5090", "BA5090")

  def aBaReport: BAreports = {
    val ctx          = JAXBContext.newInstance("ebars.xml")
    val unmarshaller = ctx.createUnmarshaller()
    val streamSource = new StreamSource("test/resources/xml/CTValid2.xml")
    unmarshaller.unmarshal(streamSource, classOf[BAreports]).getValue
  }

  private val jsonString = ebarsValidator.toJson(aBaReport)

  private val report = BAReportRequest(
    UUID.randomUUID().toString,
    jsonString,
    "BA5090",
    "BA5090"
  )

  private def testEbarsGetCall(
    path: String,
    ebarsCall: VoaEbarsConnector => Future[Try[?]],
    expectedResult: Try[Int],
    responseStatus: Int,
    responseBody: String
  ): Unit =
    withWiremockServer { wireMockServer =>
      wireMockServer.stubFor(
        get(urlEqualTo(path))
          .willReturn(
            aResponse().withStatus(responseStatus)
              .withBody(responseBody)
          )
      )
    } { (port: Int, wireMockServer: WireMockServer) =>
      val result = ebarsCall(voaEbarsConnector(port))

      val httpResult = Await.result(result, timeout)
      httpResult.isSuccess mustBe expectedResult.isSuccess
      httpResult.toString mustBe expectedResult.toString

      wireMockServer.verify(getRequestedFor(urlEqualTo(path)))
    }

  private def testSendBAReport(path: String, baReport: BAReportRequest, requestContentType: String, responseStatus: Int, responseBody: String): Unit =
    withWiremockServer { wireMockServer =>
      wireMockServer.stubFor(
        post(urlEqualTo(path))
          .willReturn(
            aResponse().withStatus(responseStatus)
              .withBody(responseBody)
          )
      )
    } { (port: Int, wireMockServer: WireMockServer) =>
      val result = voaEbarsConnector(port).sendBAReport(baReport)

      val httpResult = Await.result(result, timeout)
      httpResult mustBe responseStatus

      wireMockServer.verify(postRequestedFor(urlEqualTo(path))
        .withHeader("Content-Type", equalTo(requestContentType)))
    }

  "VoaEbarsConnector" must {
    "send reports as application/x-www-form-urlencoded content" in
      testSendBAReport(uploadXmlPath, report, uploadContentType, OK, <root><result>success</result></root>.toString)

    "handle 401 Unauthorised response from eBars" in {
      val thrown = intercept[UnauthorizedException] {
        testSendBAReport(
          uploadXmlPath,
          report,
          uploadContentType,
          UNAUTHORIZED,
          <x>
            <result>error</result> <message>401 Unauthorized</message>
          </x>.toString
        )
      }
      thrown.getMessage mustBe "UNAUTHORIZED"
    }

    "handle 500 eBars server response" in {
      val thrown = intercept[RuntimeException] {
        testSendBAReport(
          uploadXmlPath,
          report,
          uploadContentType,
          INTERNAL_SERVER_ERROR,
          <x>
            <result>error</result> <message>Internal server error</message>
          </x>.toString
        )
      }
      thrown.getMessage mustBe "eBars INTERNAL_SERVER_ERROR"
    }

    "do login" in
      testEbarsGetCall(loginPath, _.validate(loginDetails), Success(OK), OK, "login successful")

    "handle 401 Unauthorised response on login" in
      testEbarsGetCall(loginPath, _.validate(loginDetails), Failure(new UnauthorizedException("Invalid credentials")), UNAUTHORIZED, "unauthorized")

    "handle 500 response on login" in
      testEbarsGetCall(
        loginPath,
        _.validate(loginDetails),
        Failure(EbarsApiError(INTERNAL_SERVER_ERROR, "Could not login")),
        INTERNAL_SERVER_ERROR,
        "eBars server error"
      )
  }

}
