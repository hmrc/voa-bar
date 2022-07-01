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

package uk.gov.hmrc.voabar.connectors

import com.google.inject.ImplementedBy
import com.typesafe.config.ConfigException

import javax.inject.{Inject, Singleton}
import play.api.http.HeaderNames
import play.api.Logging
import play.mvc.Http.Status
import uk.gov.hmrc.crypto.{ApplicationCrypto, PlainText}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.http.HttpClient
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.voabar.util.Utils
import uk.gov.hmrc.voabar.models.EbarsRequests._
import uk.gov.hmrc.voabar.models.LoginDetails

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scala.concurrent.ExecutionContext

@Singleton
class DefaultLegacyConnector @Inject()(val http: HttpClient,
                                servicesConfig: ServicesConfig,
                                utils: Utils,
                                       applicationCrypto: ApplicationCrypto) extends LegacyConnector with Logging {

  def  crypto = applicationCrypto.JsonCrypto

  val autoBarsSubmitUrl = servicesConfig.getConfString("autobars-stubs.submit_url", throw new ConfigException.Missing("autobars-stubs.submit_url"))

  val autoBarsStubBaseUrl = servicesConfig.baseUrl("autobars-stubs") + "/autobars-stubs"


  override def validate(loginDetails: LoginDetails)(implicit executionContext: ExecutionContext,
                                            headerCarrier: HeaderCarrier):Future[Try[Int]] = {

    http.POST[LoginDetails,HttpResponse](s"$autoBarsStubBaseUrl/login", loginDetails, Seq.empty).map { response =>
      response.status match {
        case Status.OK => Success(Status.OK)
        case Status.UNAUTHORIZED => Failure(new RuntimeException(
          s"Login attempt fails with username = ${loginDetails.username} password = ****, UNAUTHORIZED"))
        case _ => {
          logger.warn(s"Unable to authenticate user, other problem : status: ${response.status}, headers: ${response.headers.mkString(",")}")
          Failure(new RuntimeException(
            s"Login attempt fails with username = ${loginDetails.username} password = ****, response code: ${response.status}"))
        }
      }
    } recover {
      case ex =>
        logger.warn("Legacy validation fails with exception " + ex.getMessage)
        Failure(new RuntimeException("Legacy validation fails with exception " + ex.getMessage))
    }
  }

  private val X_EBARS_USERNAME = "X-ebars-username"
  private val X_EBARS_PASSWORD = "X-ebars-password"
  private val X_EBARS_ATTEMPT = "X-ebars-attempt"
  private val X_EBARS_UUID = "X-ebars-uuid"
  def sendBAReport(baReport: BAReportRequest)(implicit ec: ExecutionContext, headerCarrier: HeaderCarrier): Future[Int] = {


    val authHc = utils.generateHeader(LoginDetails(baReport.username, baReport.password), headerCarrier)
      .withExtraHeaders(HeaderNames.CONTENT_TYPE ->  "text/plain; charset=UTF-8")

    http.POSTString(s"${autoBarsStubBaseUrl}/${autoBarsSubmitUrl}",
      baReport.propertyReport,
      Seq(
        X_EBARS_USERNAME -> baReport.username,
        X_EBARS_PASSWORD -> crypto.encrypt(PlainText(baReport.password)).value,
        X_EBARS_ATTEMPT -> s"${baReport.attempt}",
        X_EBARS_UUID -> baReport.uuid)
    )(readRaw, authHc, ec).flatMap { response =>
      response.status match {
        case Status.OK => Future.successful(Status.OK)
        case Status.UNAUTHORIZED => Future.failed(new  RuntimeException("UNAUTHORIZED"))
        case Status.SERVICE_UNAVAILABLE => Future.failed(new  RuntimeException("eBars UNAVAILABLE"))
        case Status.INTERNAL_SERVER_ERROR => Future.failed(new  RuntimeException("eBars INTERNAL_SERVER_ERROR"))
        case status => Future.failed(new  RuntimeException(s"unspecified eBars error, status code : ${status}"))
      }
    } recoverWith {
      case ex =>
        val errorMsg = "Couldn't send BA Reports"
        logger.warn(s"$errorMsg\n${ex.getMessage}")
        Future.failed(ex)
    }
  }
}

@ImplementedBy(classOf[DefaultLegacyConnector])
trait LegacyConnector {
  def validate(loginDetails: LoginDetails)(implicit ec: ExecutionContext, headerCarrier: HeaderCarrier): Future[Try[Int]]
  def sendBAReport(baReport: BAReportRequest)(implicit ec: ExecutionContext, headerCarrier: HeaderCarrier): Future[Int]
}
