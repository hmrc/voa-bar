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

import com.google.inject.{ImplementedBy, Singleton}
import com.typesafe.config.ConfigException
import javax.inject.Inject
import models.Purpose.Purpose
import play.api.{Configuration, Environment}
import play.api.libs.json._
import uk.gov.hmrc.http.{HttpReads, HttpResponse}
import uk.gov.hmrc.http.HttpClient
import uk.gov.hmrc.voabar.util.Utils
import uk.gov.hmrc.voabar.models.LoginDetails

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DefaultEmailConnector @Inject() (val http: HttpClient,
                                       val configuration: Configuration,
                                       utils: Utils,
                                       environment: Environment)(implicit ec: ExecutionContext)
  extends EmailConnector {

  private val emailConfigPrefix = "microservice.services.email"
  if (!configuration.has(emailConfigPrefix)) throw new ConfigException.Missing(emailConfigPrefix)
  private val protocol = configuration.getOptional[String](s"$emailConfigPrefix.protocol").getOrElse("http")
  private val host = configuration.get[String](s"$emailConfigPrefix.host")
  private val port = configuration.get[String](s"$emailConfigPrefix.port")
  private val emailUrl = s"$protocol://$host:$port"
  private val needsToSendEmail = configuration.getOptional[Boolean]("needToSendEmail").getOrElse(false)
  private val email = configuration.getOptional[String]("email")
    .getOrElse(if (needsToSendEmail) throw new ConfigException.Missing("email") else "")

  implicit val rds: HttpReads[Unit] = new HttpReads[Unit] {
    override def read(method: String, url: String, response: HttpResponse): Unit = ()
  }

  def sendEmail(
                 baRefNumber: String,
                 purpose: Purpose,
                 transactionId: String,
                 username: String,
                 password: String,
                 fileName: String,
                 dateSubmitted: String,
                 errorList: String): Future[Unit] = {
    implicit val authHc = utils.generateHeader(LoginDetails(username, password))
    if (needsToSendEmail) {
      val json = Json.obj(
        "to" -> JsArray(Seq(JsString(email))),
        "templateId" -> JsString("bars_alert_transaction"),
        "parameters" -> JsObject(Seq(
          "baRefNumber" -> JsString(s"""BA : $baRefNumber Type: $purpose"""),
          "transactionId" -> JsString(s"""Transaction id : $transactionId"""),
          "fileName" -> JsString(s"""File name : $fileName"""),
          "dateSubmitted" -> JsString(s"""Date Submitted : $dateSubmitted"""),
          "errorList" -> JsString(s"""Errors $errorList""")
        )),
        "force" -> JsBoolean(false)
      )

      http.POST[JsValue, Unit](s"$emailUrl/hmrc/email", json)
    }
    else {
      Future.unit
    }
  }
}

@ImplementedBy(classOf[DefaultEmailConnector])
trait EmailConnector {
  def sendEmail(
                 baRefNumber: String,
                 purpose: Purpose,
                 transactionId: String,
                 username: String,
                 password: String,
                 fileName: String,
                 dateSubmitted: String,
                 errorList: String): Future[Unit]
}
