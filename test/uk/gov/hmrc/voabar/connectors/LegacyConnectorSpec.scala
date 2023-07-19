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

package uk.gov.hmrc.voabar.connectors

import org.mockito.captor.ArgCaptor
import org.mockito.scalatest.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status
import play.api.libs.json.{JsValue, Writes}
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse}
import uk.gov.hmrc.http.HttpClient
import uk.gov.hmrc.voabar.models.LoginDetails

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.test.Helpers._
import uk.gov.hmrc.crypto.{ApplicationCrypto, PlainText}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.voabar.util.Utils
import uk.gov.hmrc.voabar.models.EbarsRequests.BAReportRequest

import scala.io.Source

class LegacyConnectorSpec extends PlaySpec with GuiceOneAppPerSuite with MockitoSugar {

  private def servicesConfig = app.injector.instanceOf[ServicesConfig]
  private def crypto = app.injector.instanceOf[ApplicationCrypto]
  private def utils = new Utils(crypto.JsonCrypto)
  private implicit val hc: HeaderCarrier = HeaderCarrier()

  private val username = "ba0121"
  private val password = "wibble"
  private def encryptedPassword = crypto.JsonCrypto.encrypt(PlainText(password)).value

  private def goodLogin = LoginDetails(username, encryptedPassword)
  private lazy val validXmlContent = Source.fromInputStream(getClass.getResourceAsStream("/xml/CTValid1.xml")).getLines().mkString
  private val uuid = "7f824470-9a90-42e9-927d-17b4176ed086"
  private lazy val baReportsRequest = BAReportRequest(uuid, validXmlContent, username, password)

  def getHttpMock(returnedStatus: Int): HttpClient = {
    val httpMock = mock[HttpClient]
    when(httpMock.POST(any[String], any[JsValue], any[Seq[(String, String)]])(any[Writes[JsValue]], any[HttpReads[Any]],
      any[HeaderCarrier], any[ExecutionContext])) thenReturn Future.successful(HttpResponse(returnedStatus, ""))
    httpMock
  }

  "LegacyConnector " must {
    "Send the contact details returning a 200 when it succeeds" in {
      val httpMock = getHttpMock(Status.OK)
      val connector = new DefaultLegacyConnector(httpMock, servicesConfig, utils, crypto)
      val result = await(connector.validate(goodLogin))
      result.isSuccess mustBe true
      result.get mustBe Status.OK
    }

    "return a failure representing the error when the send contact details method fails" in {
      val httpMock = getHttpMock(Status.INTERNAL_SERVER_ERROR)
      val connector = new DefaultLegacyConnector(httpMock, servicesConfig, utils, crypto)
      val result = await(connector.validate(goodLogin))

      result.isFailure mustBe true
    }

    "return a 200 when an BA report upload request is successful" in {
      val httpMock = mock[HttpClient]
      when(httpMock.POSTString(any[String], any[String], any[Seq[(String, String)]])(any[HttpReads[Any]],
        any[HeaderCarrier], any[ExecutionContext])) thenReturn Future.successful(HttpResponse(OK, ""))

      val utilsMock = mock[Utils]
      when(utilsMock.generateHeader(any[LoginDetails], any[HeaderCarrier])).thenReturn(hc)
      val connector = new DefaultLegacyConnector(httpMock, servicesConfig, utilsMock, crypto)

      connector.sendBAReport(baReportsRequest).map { result =>
        result mustBe OK
      }

    }

    "return an internal servererror when an BA report upload request fails" in {
      val httpMock = mock[HttpClient]
      when(httpMock.POSTString(any[String], any[String], any[Seq[(String, String)]])(any[HttpReads[Any]],
        any[HeaderCarrier], any[ExecutionContext])) thenReturn Future.successful(HttpResponse(Status.INTERNAL_SERVER_ERROR, ""))

      val utilsMock = mock[Utils]
      when(utilsMock.generateHeader(any[LoginDetails], any[HeaderCarrier])).thenReturn(hc)
      val connector = new DefaultLegacyConnector(httpMock, servicesConfig, utilsMock, crypto)

      connector.sendBAReport(baReportsRequest).map { _=>
        fail("we didn't expect successful future")
      } recover {
        case x: RuntimeException => succeed
      }

    }

    "provided with JSON directly" must {
      "call the Microservice" in {
        val urlCaptor = ArgCaptor[String]
        val httpMock = getHttpMock(Status.OK)

        val connector = new DefaultLegacyConnector(httpMock, servicesConfig, utils, crypto)
        connector.validate(goodLogin)

        verify(httpMock).POST(urlCaptor, any[JsValue], any[Seq[(String, String)]])(any[Writes[JsValue]], any[HttpReads[Any]],
          any[HeaderCarrier], any[ExecutionContext])
        urlCaptor.value must endWith("autobars-stubs/login")
      }

      "return a 200 if the data transfer call is successful" in {
        val connector = new DefaultLegacyConnector(getHttpMock(Status.OK), servicesConfig, utils, crypto)
        val result = await(connector.validate(goodLogin))
        result.isSuccess mustBe true
        result.get mustBe Status.OK
      }

      "throw an failure if the data transfer call fails" in {
        val connector = new DefaultLegacyConnector(getHttpMock(Status.INTERNAL_SERVER_ERROR), servicesConfig, utils, crypto)
        val result = await(connector.validate(goodLogin))
        assert(result.isFailure)
      }

      "return a failure if the data transfer call throws an exception" in {
        val httpMock = mock[HttpClient]
        when(httpMock.POST(any[String], any[JsValue], any[Seq[(String, String)]])(any[Writes[JsValue]], any[HttpReads[Any]],
          any[HeaderCarrier], any[ExecutionContext])) thenReturn Future.successful(new RuntimeException)
        val connector = new DefaultLegacyConnector(httpMock, servicesConfig, utils, crypto)
        val result = await(connector.validate(goodLogin))
        assert(result.isFailure)
      }
    }
  }

}
