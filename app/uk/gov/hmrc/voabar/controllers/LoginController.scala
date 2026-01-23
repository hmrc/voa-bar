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

package uk.gov.hmrc.voabar.controllers

import play.api.Logging
import play.api.libs.json.{JsError, JsSuccess, JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.crypto.{ApplicationCrypto, Crypted}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.voabar.connectors.{VoaBarAuditConnector, VoaEbarsConnector}
import uk.gov.hmrc.voabar.models.LoginDetails

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class LoginController @Inject() (
  val voaEbarsConnector: VoaEbarsConnector,
  audit: VoaBarAuditConnector,
  applicationCrypto: ApplicationCrypto,
  controllerComponents: ControllerComponents
)(implicit ec: ExecutionContext
) extends BackendController(controllerComponents)
  with Logging {

  private val crypto = applicationCrypto.JsonCrypto

  def verifyLogin(json: Option[JsValue]): Either[String, LoginDetails] =
    json match {
      case Some(value) =>
        val model = Json.fromJson[LoginDetails](value)
        model match {
          case JsSuccess(loginDetails, _) =>
            Right(loginDetails.copy(password = crypto.decrypt(Crypted(loginDetails.password)).value))
          case JsError(_)                 => Left(s"Unable to parse $value")
        }
      case None        => Left("No Json available")
    }

  def login: Action[AnyContent] = Action.async { implicit request =>
    verifyLogin(request.body.asJson) match {
      case Right(loginDetails) =>
        val result = voaEbarsConnector.validate(loginDetails)
        result map {
          case Success(_)  =>
            audit.userLogin(loginDetails.username)
            Ok
          case Failure(ex) =>
            logger.warn("Validating login fails with message " + ex.getMessage, ex)
            BadRequest("Validating login fails with message " + ex.getMessage)
        }
      case Left(error)         =>
        logger.warn(error)
        Future.successful(BadRequest(error))
    }
  }

}
