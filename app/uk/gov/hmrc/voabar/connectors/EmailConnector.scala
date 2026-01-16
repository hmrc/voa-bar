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

package uk.gov.hmrc.voabar.connectors

import com.google.inject.{ImplementedBy, Singleton}
import com.typesafe.config.ConfigException
import models.Purpose.Purpose
import play.api.http.Status.{ACCEPTED, OK}
import play.api.libs.json.*
import play.api.libs.ws.writeableOf_JsValue
import play.api.{Configuration, Logging}
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.voabar.models.LoginDetails
import uk.gov.hmrc.voabar.util.Utils

import java.net.URL
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DefaultEmailConnector @Inject() (
  httpClientV2: HttpClientV2,
  servicesConfig: ServicesConfig,
  configuration: Configuration,
  utils: Utils
)(implicit ec: ExecutionContext
) extends EmailConnector
  with Logging:

  private val emailServiceBase: String  = servicesConfig.baseUrl("email")
  private val sendEmailURL: URL         = url"$emailServiceBase/hmrc/email"
  private val needsToSendEmail: Boolean = configuration.getOptional[Boolean]("needToSendEmail").getOrElse(false)

  private val email = configuration.getOptional[String]("email")
    .getOrElse(if needsToSendEmail then throw new ConfigException.Missing("email") else "")

  def sendEmail(
    baRefNumber: String,
    purpose: Purpose,
    transactionId: String,
    username: String,
    password: String,
    fileName: String,
    dateSubmitted: String,
    errorList: String
  ): Future[Unit] =
    implicit val authHc: HeaderCarrier = utils.generateHeader(LoginDetails(username, password))

    if needsToSendEmail then
      val json = Json.obj(
        "to"         -> JsArray(Seq(JsString(email))),
        "templateId" -> JsString("bars_alert_transaction"),
        "parameters" -> JsObject(Seq(
          "baRefNumber"   -> JsString(s"""BA : $baRefNumber Type: $purpose"""),
          "transactionId" -> JsString(s"""Transaction id : $transactionId"""),
          "fileName"      -> JsString(s"""File name : $fileName"""),
          "dateSubmitted" -> JsString(s"""Date Submitted : $dateSubmitted"""),
          "errorList"     -> JsString(s"""Errors $errorList""")
        )),
        "force"      -> JsBoolean(false)
      )

      httpClientV2.post(sendEmailURL)
        .withBody(json)
        .execute[HttpResponse]
        .map { r =>
          r.status match {
            case OK | ACCEPTED => logger.info(s"Send email successful: ${r.status}")
            case status        => logger.error(s"Send email FAILED: $status ${r.body}")
          }
        }
    else
      Future.unit

@ImplementedBy(classOf[DefaultEmailConnector])
trait EmailConnector:

  def sendEmail(
    baRefNumber: String,
    purpose: Purpose,
    transactionId: String,
    username: String,
    password: String,
    fileName: String,
    dateSubmitted: String,
    errorList: String
  ): Future[Unit]
