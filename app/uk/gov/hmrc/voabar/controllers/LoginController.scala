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

package uk.gov.hmrc.voabar.controllers

import javax.inject.{Inject, Singleton}
import play.api.Logging
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.Future
import uk.gov.hmrc.voabar.models.LoginDetails
import play.api.libs.json.{JsError, JsSuccess, JsValue, Json}
import uk.gov.hmrc.voabar.connectors.{LegacyConnector, VoaBarAuditConnector}
import uk.gov.hmrc.voabar.dbmigration.SubmissionCreatedDateMigration

import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class LoginController @Inject()(val legacyConnector: LegacyConnector,
                                audit: VoaBarAuditConnector,
                                submissionCreatedDateMigration: SubmissionCreatedDateMigration, // TODO: Remove after 1 April 2023
                                controllerComponents: ControllerComponents)
  extends BackendController(controllerComponents) with Logging {
  def verifyLogin(json: Option[JsValue]): Either[String, LoginDetails] = {
    json match {
      case Some(value) => {
        val model = Json.fromJson[LoginDetails](value)
        model match {
          case JsSuccess(contact, _) => Right(contact)
          case JsError(_) => Left("Unable to parse " + value)
        }
      }
      case None => Left("No Json available")
    }
  }

  def login(): Action[AnyContent] = Action.async { implicit request =>
    verifyLogin(request.body.asJson) match {
      case Right(loginDetails) => {
        val result = legacyConnector.validate(loginDetails)
        result map {
          case Success(s) => {
            audit.userLogin(loginDetails.username)
            Ok
          }
          case Failure(ex) =>
            logger.warn("Validating login fails with message " + ex.getMessage)
            BadRequest("Validating login fails with message " + ex.getMessage)
        }
      }
      case Left(error) => {
        logger.warn(error)
        Future.successful(BadRequest(error))
      }
    }
  }
}
