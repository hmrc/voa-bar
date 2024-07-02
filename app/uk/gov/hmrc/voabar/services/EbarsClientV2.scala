/*
 * Copyright 2024 HM Revenue & Customs
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

import org.apache.pekko.stream.Materializer
import play.api.http.Status
import play.api.libs.ws.ahc.{AhcConfigBuilder, AhcWSClient, StandaloneAhcWSClient}
import play.api.libs.ws.{WSAuthScheme, WSClient, WSResponse, writeableOf_urlEncodedForm}
import play.api.{Configuration, Logging}
import play.shaded.ahc.org.asynchttpclient.proxy.{ProxyServer, ProxyType}
import play.shaded.ahc.org.asynchttpclient.{AsyncHttpClient, DefaultAsyncHttpClient, Realm}
import uk.gov.hmrc.http.UnauthorizedException
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.util.Collections
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}
import scala.xml.XML

// TODO: use uk.gov.hmrc.http.HttpClient with WSProxy
@Singleton
class EbarsClientV2 @Inject()(
                                     servicesConfig: ServicesConfig,
                                     configuration: Configuration)
                             (implicit ec: ExecutionContext, materialize: Materializer)
  extends Logging with Status {

  private val voaEbarsBaseUrl = servicesConfig.baseUrl("voa-ebars")
  private val xmlFileUploadUrl = s"$voaEbarsBaseUrl/ebars_dmz_pres_ApplicationWeb/uploadXmlSubmission"
  private val timeout = 120 seconds

  private val ws: WSClient = {
    val proxyAhcConfig = configuration.getOptional[Boolean]("proxy.enabled") flatMap {
      case true => Some {
        val proxyHost = configuration.get[String]("proxy.host")
        val proxyPort = configuration.get[Int]("proxy.port")
        val proxyUsername = configuration.get[String]("proxy.username")
        val proxyPassword = configuration.get[String]("proxy.password")
        val realm = new Realm.Builder(proxyUsername, proxyPassword)
          .setScheme(Realm.AuthScheme.BASIC)
          .setUsePreemptiveAuth(true)
          .build()

        new AhcConfigBuilder().modifyUnderlying {
          _.setProxyServer(new ProxyServer(proxyHost, proxyPort, proxyPort, realm, Collections.emptyList(), ProxyType.HTTP))
        }.build()
      }
      case _ => None
    }

    val clientConfig = proxyAhcConfig.getOrElse(new AhcConfigBuilder().build())
    val asyncHttpClient: AsyncHttpClient = new DefaultAsyncHttpClient(clientConfig)
    val standaloneClient: StandaloneAhcWSClient = new StandaloneAhcWSClient(asyncHttpClient)
    new AhcWSClient(standaloneClient)
  }


  def uploadXMl(username: String, password: String, xml: String, attempt: Int): Future[Try[Int]] =
    ws.url(xmlFileUploadUrl)
      .withAuth(username, password, WSAuthScheme.BASIC)
      .withRequestTimeout(timeout)
      .post(Map("xml" -> Seq(xml)))
      .map(processResponse(attempt))

  private def processResponse(attempt: Int)(response: WSResponse): Try[Int] = {
    logger.trace(s"Response : $response")
    response.status match {
      case Status.OK => parseOkResponse(response, attempt)
      case Status.UNAUTHORIZED => Failure(new UnauthorizedException("UNAUTHORIZED"))
      case status =>
        logger.warn(s"Couldn't send BA Reports. status: $status\n${response.body}")
        Failure(EbarsApiError(status, s"${response.statusText}. attempt: $attempt"))
    }
  }

  private def parseOkResponse(response: WSResponse, attempt: Int): Try[Int] = {
    val body = response.body
    if (body.contains("401 Unauthorized")) {
      Failure(new UnauthorizedException("UNAUTHORIZED"))
    } else {
      Try {
        val responseXML = XML.loadString(body)
        val status = (responseXML \ "result").text
        status match {
          case "success" => Success(OK)
          case "error" =>
            val errorDetail = (responseXML \ "message").text
            logger.warn(s"Couldn't send BA Reports. error: $errorDetail")
            Failure(EbarsApiError(OK, s"$errorDetail. attempt: $attempt"))
        }
      } getOrElse {
        logger.warn(s"Parsing eBars response failed. Body:\n$body")
        Failure(EbarsApiError(OK, s"Parsing eBars response failed. attempt: $attempt"))
      }
    }
  }

}

case class EbarsApiError(status: Int, message: String) extends RuntimeException(s"$status. $message")
