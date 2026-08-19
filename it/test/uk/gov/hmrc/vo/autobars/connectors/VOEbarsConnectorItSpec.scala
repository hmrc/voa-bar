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
import ebars.xml.BAreports
import jakarta.xml.bind.JAXBContext
import org.scalatest.Assertion
import play.api.Configuration
import play.api.test.FutureAwaits
import play.api.test.Helpers.*
import services.EbarsValidator
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, UnauthorizedException}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.vo.autobars.connectors.{DefaultVOEbarsConnector, VOBarAuditConnector, VOEbarsConnector}
import uk.gov.hmrc.vo.autobars.models.EbarsRequests.BAReportRequest
import uk.gov.hmrc.vo.autobars.models.LoginDetails
import uk.gov.hmrc.vo.autobars.services.{EbarsApiError, EbarsClientV2}
import uk.gov.hmrc.vo.integration.test.BaseServerSpec

import java.util.UUID
import javax.xml.transform.stream.StreamSource
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

class VOEbarsConnectorItSpec extends BaseServerSpec with FutureAwaits:

  private def voEbarsConnector(port: Int): VOEbarsConnector =
    val config         = inject[Configuration]
    val servicesConfig = ServicesConfig(Configuration("microservice.services.voa-ebars.port" -> port).withFallback(config))

    val eBarsClientV2 = EbarsClientV2(inject[HttpClientV2], servicesConfig)
    DefaultVOEbarsConnector(eBarsClientV2, inject[VOBarAuditConnector])

  given ExecutionContext = inject[ExecutionContext]
  given HeaderCarrier    = HeaderCarrier()

  private val eBarsValidator = EbarsValidator()
  private val loginDetails   = LoginDetails("BA5090", "BA5090")
  private val jsonString     = eBarsValidator.toJson(aBaReport)

  private val loginPath         = "/ebars_dmz_pres_ApplicationWeb/Welcome.do"
  private val uploadXmlPath     = "/ebars_dmz_pres_ApplicationWeb/uploadXmlSubmission"
  private val uploadContentType = "application/x-www-form-urlencoded"

  private def aBaReport: BAreports =
    val ctx          = JAXBContext.newInstance("ebars.xml")
    val unmarshaller = ctx.createUnmarshaller()
    val streamSource = StreamSource("test/resources/xml/CTValid2.xml")
    unmarshaller.unmarshal(streamSource, classOf[BAreports]).getValue

  private val report = BAReportRequest(
    UUID.randomUUID.toString,
    jsonString,
    "BA5090",
    "BA5090"
  )

  private def testEbarsGetCall(
    path: String,
    eBarsCall: VOEbarsConnector => Future[Try[?]],
    expectedResult: Try[Int],
    responseStatus: Int,
    responseBody: String
  ): Assertion =
    wireMockServer.stubFor(
      get(urlEqualTo(path))
        .willReturn(
          aResponse().withStatus(responseStatus)
            .withBody(responseBody)
        )
    )

    val result = await(eBarsCall(voEbarsConnector(wireMockServer.port)))

    wireMockServer.verify(getRequestedFor(urlEqualTo(path)))

    result.isSuccess shouldBe expectedResult.isSuccess
    result.toString  shouldBe expectedResult.toString

  private def testSendBAReport(
    path: String,
    baReport: BAReportRequest,
    requestContentType: String,
    responseStatus: Int,
    responseBody: String
  ): Assertion =
    wireMockServer.stubFor(
      post(urlEqualTo(path))
        .willReturn(
          aResponse().withStatus(responseStatus)
            .withBody(responseBody)
        )
    )

    val result = await(voEbarsConnector(wireMockServer.port).sendBAReport(baReport))

    wireMockServer.verify(postRequestedFor(urlEqualTo(path))
      .withHeader("Content-Type", equalTo(requestContentType)))

    result shouldBe responseStatus

  "VO eBars сonnector" should {
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
      thrown.getMessage shouldBe "UNAUTHORIZED"
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
      thrown.getMessage shouldBe "eBars INTERNAL_SERVER_ERROR"
    }

    "do login" in
      testEbarsGetCall(loginPath, _.validate(loginDetails), Success(OK), OK, "login successful")

    "handle 401 Unauthorised response on login" in
      testEbarsGetCall(loginPath, _.validate(loginDetails), Failure(UnauthorizedException("Invalid credentials")), UNAUTHORIZED, "unauthorized")

    "handle 500 response on login" in
      testEbarsGetCall(
        loginPath,
        _.validate(loginDetails),
        Failure(EbarsApiError(INTERNAL_SERVER_ERROR, "500. Could not login")),
        INTERNAL_SERVER_ERROR,
        "eBars server error"
      )
  }
