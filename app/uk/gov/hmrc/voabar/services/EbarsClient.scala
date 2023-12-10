/*
 * Copyright 2022 HM Revenue & Customs
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

import net.htmlparser.jericho.Source
import org.apache.http.HttpStatus._
import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods._
import org.apache.http.impl.client.{BasicCookieStore, BasicCredentialsProvider, CloseableHttpClient, HttpClients}
import org.apache.http.util.{EntityUtils, VersionInfo}
import org.apache.http.{HttpHost, HttpResponse}
import play.api.{Configuration, Logging}
import uk.gov.hmrc.http.UnauthorizedException
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters._
import scala.language.postfixOps
import scala.util.Try

// TODO: use uk.gov.hmrc.http.HttpClient with WSProxy. Move login() to EbarsClientV2
class EbarsClient(username: String, password: String, servicesConfig: ServicesConfig, configuration: Configuration)
  extends AutoCloseable with Logging {

  private val voaEbarsBaseUrl = servicesConfig.baseUrl("voa-ebars")
  private val loginUrl = s"$voaEbarsBaseUrl/ebars_dmz_pres_ApplicationWeb/Welcome.do"
  private val timeout = 120 seconds

  private val httpClient: CloseableHttpClient = {
    val cookieStore = new BasicCookieStore
    val userAgent = VersionInfo.getUserAgent("Ebars-Apache-HttpClient", "org.apache.http.client", getClass)
    HttpClients.custom()
      .setDefaultRequestConfig(config)
      .setDefaultCookieStore(cookieStore)
      .setDefaultCredentialsProvider(credsProvider)
      .setUserAgent(userAgent)
      .build()
  }

  private def proxyHttpHost = {
    val proxyHost = configuration.get[String]("proxy.host")
    val proxyPort = configuration.get[Int]("proxy.port")
    val proxyProtocol = configuration.get[String]("proxy.protocol")
    new HttpHost(proxyHost, proxyPort, proxyProtocol)
  }

  private def credsProvider = {
    val bcp = new BasicCredentialsProvider

    proxy match {
      case Some(_) =>
        val proxyUsername = configuration.get[String]("proxy.username")
        val proxyPassword = configuration.get[String]("proxy.password")

        bcp.setCredentials(new AuthScope(proxyHttpHost), new UsernamePasswordCredentials(proxyUsername, proxyPassword))
      case _ =>
    }

    bcp.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password))

    bcp
  }

  private def config = proxy.fold(RequestConfig.custom())(p => RequestConfig.custom().setProxy(p))
    .setSocketTimeout(timeout.toMillis.toInt)
    .setConnectTimeout(timeout.toMillis.toInt)
    .setContentCompressionEnabled(false)
    .build()

  private def proxy: Option[HttpHost] = configuration.getOptional[Boolean]("proxy.enabled").filter(identity).map(_ => proxyHttpHost)

  def login: Try[Int] = Try {
    httpClient.execute(new HttpGet(loginUrl), (response: HttpResponse) => {
      val html = EntityUtils.toString(response.getEntity)
      val errors = extractErrors(html)

      response.getStatusLine.getStatusCode match {
        case SC_UNAUTHORIZED | SC_FORBIDDEN => throw new UnauthorizedException("Invalid credentials")
        case SC_OK if errors.nonEmpty =>
          logger.warn(s"Login errors: $errors\n$html")
          throw EbarsApiError(SC_OK, errors.toString)
        case SC_OK if isSessionExpired(html) =>
          logger.warn(s"Session expired\n$html")
          throw EbarsApiError(SC_OK, "Session expired")
        case SC_OK if errors.isEmpty => SC_OK
        case status =>
          logger.warn(s"Couldn't login. status: $status\n$html")
          throw EbarsApiError(status, "Could not login")
      }
    })
  }

  private def isSessionExpired(htmlContent: String) = htmlContent.contains("Your session has expired, you will need to login again")

  private def extractErrors(htmlContent: String) = {
    val source = new Source(htmlContent)
    val errors = source.getAllElements("name", "errorMsg", true).asScala.toList.map(t => models.Error("voa-ebars-0002", t.getAttributeValue("value")))

    val errors2 = source.getAllElements().asScala.filter { e =>
      e.getAttributeValue("style") == "font-family: 'MS Reference Sans Serif', 'Verdana Ref', sans-serif;font-size: 12px;color:red;"
    }.map(t => models.Error("voa-ebars-0003", t.getTextExtractor.toString)).toList

    errors ++ errors2
  }

  override def close(): Unit = httpClient.close()
}
