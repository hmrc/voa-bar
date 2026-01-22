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

package uk.gov.hmrc.voabar.services

import play.api.Logging
import play.api.http.HeaderNames.AUTHORIZATION
import play.api.http.Status.{INTERNAL_SERVER_ERROR, OK, UNAUTHORIZED}
import play.api.libs.ws.writeableOf_urlEncodedForm
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UnauthorizedException}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.net.URL
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}
import scala.xml.XML

@Singleton
class EbarsClientV2 @Inject() (
  httpClientV2: HttpClientV2,
  servicesConfig: ServicesConfig
)(implicit ec: ExecutionContext
) extends Logging:

  private val voaEbarsBaseUrl: String = servicesConfig.baseUrl("voa-ebars")
  private val xmlFileUploadURL: URL   = url"$voaEbarsBaseUrl/ebars_dmz_pres_ApplicationWeb/uploadXmlSubmission"
  private val timeout: FiniteDuration = 120 seconds

  private def basicAuth(clientId: String, clientSecret: String): String =
    val encodedCredentials = Base64.getEncoder.encodeToString(s"$clientId:$clientSecret".getBytes(UTF_8))
    s"Basic $encodedCredentials"

  def uploadXML(username: String, password: String, xml: String, attempt: Int): Future[Try[Int]] =
    httpClientV2.post(xmlFileUploadURL)(using HeaderCarrier())
      .setHeader(AUTHORIZATION -> basicAuth(username, password))
      .withBody(Map("xml" -> Seq(xml)))
      .withProxy
      .transform(_.withRequestTimeout(timeout))
      .execute[HttpResponse]
      .map(processResponse(attempt))

  private def processResponse(attempt: Int)(response: HttpResponse): Try[Int] =
    logger.trace(s"Response : $response")
    response.status match {
      case OK           => parseOkResponse(response, attempt)
      case UNAUTHORIZED => Failure(new UnauthorizedException("UNAUTHORIZED"))
      case status       =>
        logger.warn(s"Couldn't send BA Reports. status: $status\n${response.body}")
        Failure(EbarsApiError(status, s"${response.status}. attempt: $attempt"))
    }

  private def parseOkResponse(response: HttpResponse, attempt: Int): Try[Int] =
    val body = response.body
    if body.contains("401 Unauthorized") then
      Failure(new UnauthorizedException("UNAUTHORIZED"))
    else
      Try {
        val responseXML = XML.loadString(body)
        val status      = (responseXML \ "result").text
        status match {
          case "success" => Success(OK)
          case "error"   =>
            val errorDetail = (responseXML \ "message").text
            logger.warn(s"Couldn't send BA Reports. error: $errorDetail")
            Failure(EbarsApiError(INTERNAL_SERVER_ERROR, s"$errorDetail. attempt: $attempt"))
        }
      } getOrElse {
        logger.warn(s"Parsing eBars response failed. Body:\n$body")
        Failure(EbarsApiError(INTERNAL_SERVER_ERROR, s"Parsing eBars response failed. attempt: $attempt"))
      }

case class EbarsApiError(status: Int, message: String) extends RuntimeException(s"$status. $message")
