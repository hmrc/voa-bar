/*
 * Copyright 2020 HM Revenue & Customs
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
import play.api.libs.json.{JsSuccess, JsValue}
import play.api.mvc.{Action, ControllerComponents, Request, Result}
import uk.gov.hmrc.play.bootstrap.controller.{BackendController, BaseController}
import uk.gov.hmrc.voabar.models.ReportStatus
import uk.gov.hmrc.voabar.repositories.SubmissionStatusRepository
import play.api.libs.json.Json
import uk.gov.hmrc.voabar.services.WebBarsService

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SubmissionStatusController @Inject() (
                                           submissionStatusRepository: SubmissionStatusRepository,
                                           controllerComponents: ControllerComponents,
                                           webBarsService: WebBarsService
                                           )(implicit ec: ExecutionContext) extends BackendController(controllerComponents) {

  private def getReportStatusesByUser(userId: String, filter: Option[String]): Future[Either[Result, Seq[ReportStatus]]] = {
    submissionStatusRepository.getByUser(userId, filter).map(_.fold(
      _ => Left(InternalServerError),
      reportStatuses => Right(reportStatuses)
    ))
  }

  private def getAllReportStatuses(): Future[Either[Result, Seq[ReportStatus]]] = {
    submissionStatusRepository.getAll().map(_.fold(
      _ => Left(InternalServerError),
      reportStatuses => Right(reportStatuses)
    ))
  }

  def getByUser(filter: Option[String] = None) = Action.async { implicit request =>
    (for {
      userId <- EitherT.fromOption[Future](request.headers.get("BA-Code"), Unauthorized("BA-Code missing"))
      reportStatuses <- EitherT(getReportStatusesByUser(userId, filter))
    } yield (Ok(Json.toJson(reportStatuses))))
        .valueOr(_ => InternalServerError)
  }

  def getAll() = Action.async { implicit request =>
    (for {
      userId <- EitherT.fromOption[Future](request.headers.get("BA-Code"), Unauthorized("BA-Code missing"))
      reportStatuses <- EitherT(getAllReportStatuses())
    } yield (Ok(Json.toJson(reportStatuses))))
      .valueOr(_ => InternalServerError)
  }

  private def getReportStatusByReference(reference: String): Future[Either[Result, ReportStatus]] = {
    submissionStatusRepository.getByReference(reference).map(_.fold(
      _ => Left(InternalServerError),
      reportStatuses => Right(reportStatuses)
    ))
  }

  def getByReference(reference: String) = Action.async { implicit request =>
    (for {
      _ <- EitherT.fromOption[Future](request.headers.get("BA-Code"), Unauthorized("BA-Code missing"))
      reportStatuses <- EitherT(getReportStatusByReference(reference))
    } yield (Ok(Json.toJson(reportStatuses))))
      .valueOr(_ => InternalServerError)
  }

  private def parseReportStatus(request: Request[JsValue]): Either[Result, ReportStatus] = {
    request.body.validate[ReportStatus] match {
      case result: JsSuccess[ReportStatus] => Right(result.get)
      case _ => Left(BadRequest)
    }
  }

  private def saveSubmission(reportStatus: ReportStatus, upsert: Boolean): Future[Either[Result, Unit.type]] = {
    submissionStatusRepository.insertOrMerge(reportStatus).map(_.fold(
      _ => Left(InternalServerError),
      _ => {
        if(reportStatus.report.isDefined) {
          webBarsService.newSubmission(reportStatus.id) //Fire and forget.
        }
        Right(Unit)
      }
    ))
  }


  private def saveSubmissionUserInfo(userId: String, reference: String, upsert: Boolean)
    : Future[Either[Result, Unit.type]] = {
    submissionStatusRepository.saveOrUpdate(userId, reference, upsert).map(_.fold(
      _ => Left(InternalServerError),
      _ => Right(Unit)
    ))
  }

  def save(upsert: Boolean = false) = Action.async(parse.tolerantJson) { request =>
    (for {
      reportStatus <- EitherT.fromEither[Future](parseReportStatus(request))
      _ <- EitherT(saveSubmission(reportStatus, upsert))
    } yield NoContent)
      .valueOr(_ => InternalServerError)
  }

  def saveUserInfo() = Action.async(parse.tolerantJson) { request =>
    (for {
      reportStatus <- EitherT.fromEither[Future](parseReportStatus(request))
      _ <- EitherT(saveSubmissionUserInfo(reportStatus.baCode.get, reportStatus.id, true))
    } yield NoContent)
      .valueOr(_ => InternalServerError)
  }
}
