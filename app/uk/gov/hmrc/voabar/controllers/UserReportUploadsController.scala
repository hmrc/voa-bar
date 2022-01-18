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

import cats.data.EitherT
import cats.implicits._

import javax.inject.{Inject, Singleton}
import play.api.Logging
import play.api.libs.json.{JsSuccess, JsValue, Json}
import play.api.mvc.{ControllerComponents, Request, Result}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.voabar.repositories.{UserReportUpload, UserReportUploadsRepository}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UserReportUploadsController @Inject() (userReportUploadsRepository: UserReportUploadsRepository,
                                             controllerComponents: ControllerComponents
                                            )(implicit ec: ExecutionContext) extends BackendController(controllerComponents) with Logging {

  def getById(id: String) = Action.async {
    userReportUploadsRepository.getById(id).map(_.fold(
      _ => InternalServerError,
      userReportUpload => Ok(Json.toJson(userReportUpload))
    ))
  }

  private def parseUserReportUpload(request: Request[JsValue]) = {
    request.body.validate[UserReportUpload] match {
      case userReportUpload: JsSuccess[UserReportUpload] => Right(userReportUpload.get)
      case _ => {
        logger.error(s"Couln't parse:\n${request.body.toString}")
        Left(BadRequest)
      }
    }
  }

  private def saveUserReportUpload(userReportUpload: UserReportUpload): Future[Either[Result, Unit]] = {
    userReportUploadsRepository.save(userReportUpload).map(_.fold(
      _ => Left(InternalServerError),
      _ => Right(Unit)
    ))
  }

  def save() = Action.async(parse.tolerantJson) { implicit request =>
    (for {
      userReportUpload <- EitherT.fromEither[Future](parseUserReportUpload(request))
      _ <- EitherT(saveUserReportUpload(userReportUpload))
    } yield NoContent)
      .valueOr(_ => InternalServerError)
  }
}
