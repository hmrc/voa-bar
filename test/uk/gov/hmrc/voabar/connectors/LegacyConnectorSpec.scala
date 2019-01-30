/*
 * Copyright 2019 HM Revenue & Customs
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

import org.mockito.ArgumentCaptor
import org.mockito.Matchers.{any, anyString}
import org.mockito.Mockito.{verify, when}
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.{Play, Configuration, Environment}
import play.api.http.Status
import play.api.inject.Injector
import play.api.libs.json.{JsValue, Writes}
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.voabar.models.LoginDetails

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.test.Helpers._
import uk.gov.hmrc.crypto.{ApplicationCrypto,PlainText}
import uk.gov.hmrc.voabar.Utils
import uk.gov.hmrc.voabar.models.EbarsRequests.BAReportRequest

import scala.io.Source

class LegacyConnectorSpec extends PlaySpec with GuiceOneAppPerSuite with MockitoSugar {

  private def injector: Injector = app.injector
  private val configuration = injector.instanceOf[Configuration]
  private val environment = injector.instanceOf[Environment]
  private val crypto = new ApplicationCrypto(configuration.underlying)
  private val utils = new Utils(crypto.JsonCrypto)
  private implicit val hc = mock[HeaderCarrier]

  private val username = "ba0121"
  private val password = "wibble"
  private val encryptedPassword = crypto.JsonCrypto.encrypt(PlainText(password)).value

  private val goodLogin = LoginDetails(username, encryptedPassword)
  private lazy val validXmlContent = Source.fromInputStream(getClass.getResourceAsStream("/xml/CTValid1.xml")).getLines().mkString
  private val uuid = "7f824470-9a90-42e9-927d-17b4176ed086"
  private lazy val baReportsRequest = BAReportRequest(uuid, validXmlContent, username, password)

  def getHttpMock(returnedStatus: Int): HttpClient = {
    val httpMock = mock[HttpClient]
    when(httpMock.POST(anyString, any[JsValue], any[Seq[(String, String)]])(any[Writes[Any]], any[HttpReads[Any]],
      any[HeaderCarrier], any())) thenReturn Future.successful(HttpResponse(returnedStatus, None))
    when(httpMock.POSTString(anyString, any[String], any[Seq[(String, String)]])(any[HttpReads[Any]],
      any[HeaderCarrier], any())) thenReturn Future.successful(HttpResponse(returnedStatus, None))
    when(httpMock.GET(anyString)(any[HttpReads[Any]], any[HeaderCarrier], any())) thenReturn Future.successful(HttpResponse(returnedStatus, None))
    httpMock
  }

  "LegacyConnector " must {
    "Send the contact details returning a 200 when it succeeds" in {
      val httpMock = getHttpMock(Status.OK)
      val connector = new DefaultLegacyConnector(httpMock, configuration, utils, environment)
      val result = await(connector.validate(goodLogin))
      result.isSuccess mustBe true
      result.get mustBe Status.OK
    }

    "return a failure representing the error when the send contact details method fails" in {
      val httpMock = getHttpMock(Status.INTERNAL_SERVER_ERROR)
      val connector = new DefaultLegacyConnector(httpMock, configuration, utils, environment)
      val result = await(connector.validate(goodLogin))

      result.isFailure mustBe true
    }

    "return a 200 when an BA report upload request is successful" in {
      val httpMock = getHttpMock(Status.OK)
      val utilsMock = mock[Utils]
      when(utilsMock.decryptPassword(any[String])).thenReturn(password)
      when(utilsMock.generateHeader(any[LoginDetails])(any[ExecutionContext])).thenReturn(hc)
      val connector = new DefaultLegacyConnector(httpMock, configuration, utilsMock, environment)

      val result = await(connector.sendBAReport(baReportsRequest))

      result.isSuccess mustBe true
      result.get mustBe Status.OK
    }

    "return an internal servererror when an BA report upload request fails" in {
      val httpMock = getHttpMock(Status.INTERNAL_SERVER_ERROR)
      val utilsMock = mock[Utils]
      when(utilsMock.decryptPassword(any[String])).thenReturn(password)
      when(utilsMock.generateHeader(any[LoginDetails])(any[ExecutionContext])).thenReturn(hc)
      val connector = new DefaultLegacyConnector(httpMock, configuration, utilsMock, environment)

      val result = await(connector.sendBAReport(baReportsRequest))

      result.isFailure mustBe true
    }

    "provided with JSON directly" must {
      "call the Microservice" in {
        implicit val headerCarrierNapper = ArgumentCaptor.forClass(classOf[HeaderCarrier])
        implicit val httpReadsNapper = ArgumentCaptor.forClass(classOf[HttpReads[Any]])
        val urlCaptor = ArgumentCaptor.forClass(classOf[String])
        val httpMock = getHttpMock(Status.OK)

        val connector = new DefaultLegacyConnector(httpMock, configuration, utils, environment)
        connector.validate(goodLogin)

        verify(httpMock).POST(urlCaptor.capture, any(),any())(any(), any(), any(), any())
        urlCaptor.getValue must endWith("autobars-stubs/login")
      }

      "return a 200 if the data transfer call is successful" in {
        val connector = new DefaultLegacyConnector(getHttpMock(Status.OK), configuration, utils, environment)
        val result = await(connector.validate(goodLogin))
        result.isSuccess mustBe true
        result.get mustBe Status.OK
      }

      "throw an failure if the data transfer call fails" in {
        val connector = new DefaultLegacyConnector(getHttpMock(Status.INTERNAL_SERVER_ERROR), configuration, utils, environment)
        val result = await(connector.validate(goodLogin))
        assert(result.isFailure)
      }

      "return a failure if the data transfer call throws an exception" in {
        val httpMock = mock[HttpClient]
        when(httpMock.POST(anyString, any(), any())(any(), any[HttpReads[Any]], any[HeaderCarrier], any())) thenReturn Future.successful(new RuntimeException)
        val connector = new DefaultLegacyConnector(httpMock, configuration, utils, environment)
        val result = await(connector.validate(goodLogin))
        assert(result.isFailure)
      }
    }
  }
}
