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

import models.Purpose
import org.mockito.Matchers.{any, anyString}
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status
import play.api.{Configuration, Environment}
import play.api.inject.Injector
import play.api.libs.json.{JsObject, JsValue, Writes}
import uk.gov.hmrc.crypto.ApplicationCrypto
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.voabar.Utils

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global

class EmailConnectorSpec extends PlaySpec with GuiceOneAppPerSuite with MockitoSugar  {

  private def injector: Injector = app.injector
  private val configuration = injector.instanceOf[Configuration]
  private val environment = injector.instanceOf[Environment]
  private val crypto = new ApplicationCrypto(configuration.underlying)
  private val utils = new Utils(crypto.JsonCrypto)
  private implicit val hc = mock[HeaderCarrier]
  private val username = "username"
  private val password = "password"
  private val baCode = "BA1234"
  private val purpose = Purpose.CT
  private val submissionId = "submissionId"
  private val filename = "filename.xml"
  private val date = "2000-01-01"

  def getHttpMock(returnedStatus: Int): HttpClient = {
    val httpMock = mock[HttpClient]
    when(httpMock.POST(anyString, any[JsValue], any[Seq[(String, String)]])(any[Writes[Any]], any[HttpReads[Any]],
      any[HeaderCarrier], any[ExecutionContext])) thenReturn Future.successful(HttpResponse(returnedStatus, None))
    when(httpMock.POSTString(anyString, any[String], any[Seq[(String, String)]])(any[HttpReads[Any]],
      any[HeaderCarrier], any[ExecutionContext])) thenReturn Future.successful(HttpResponse(returnedStatus, None))
    when(httpMock.GET(anyString, any[Seq[(String, String)]], any[Seq[(String, String)]])(any[HttpReads[Any]], any[HeaderCarrier], any[ExecutionContext])) thenReturn Future.successful(HttpResponse(returnedStatus, None))
    httpMock
  }

  def getConfiguration(sendEmail: Boolean = true): Configuration = {
    val configuration = mock[Configuration]
    val emailConfig = mock[Configuration]
    when(emailConfig.getString("host")).thenReturn(Some("localhost"))
    when(emailConfig.getString("port")).thenReturn(Some("80"))
    when(emailConfig.getString("protocol")).thenReturn(Some("http"))
    when(configuration.getConfig("microservice.services.email")).thenReturn(Some(emailConfig))
    when(configuration.getBoolean("needToSendEmail")).thenReturn(Some(sendEmail))
    when(configuration.getString("email")).thenReturn(Some("foo@bar.co.uk"))
    configuration
  }

  "EmailConnector" must {
    "verify that the email service gets called when email needs to be sent" in {
      val httpMock = getHttpMock(Status.OK)
      val connector = new DefaultEmailConnector(httpMock, getConfiguration(), utils, environment)

      connector.sendEmail(baCode, purpose, submissionId, username, password, filename, date, "")

      verify(httpMock)
        .POST[JsObject, Unit](anyString, any[JsObject], any[Seq[(String, String)]])(any[Writes[JsObject]], any[HttpReads[Unit]], any[HeaderCarrier], any[ExecutionContext])
    }
    "verify that the email service doesn't get called when email needn't to be sent" in {
      val httpMock = getHttpMock(Status.OK)
      val connector = new DefaultEmailConnector(httpMock, getConfiguration(sendEmail = false), utils, environment)

      connector.sendEmail(baCode, purpose, submissionId, username, password, filename, date, "")

      verify(httpMock, times(0))
        .POST[JsObject, Unit](anyString, any[JsObject], any[Seq[(String, String)]])(any[Writes[JsObject]], any[HttpReads[Unit]], any[HeaderCarrier], any[ExecutionContext])
    }
  }
}
