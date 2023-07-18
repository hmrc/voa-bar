/*
 * Copyright 2023 HM Revenue & Customs
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
import play.api.{Configuration, Logger}
import play.api.libs.json.{JsSuccess, JsValue}
import play.api.mvc.{ControllerComponents, Request, Result}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.voabar.models.ReportStatus
import uk.gov.hmrc.voabar.repositories.SubmissionStatusRepository
import play.api.libs.json.Json
import uk.gov.hmrc.crypto.{ApplicationCrypto, Crypted}
import uk.gov.hmrc.voabar.services.WebBarsService

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton
class SubmissionStatusController @Inject() (
                                           submissionStatusRepository: SubmissionStatusRepository,
                                           controllerComponents: ControllerComponents,
                                           webBarsService: WebBarsService,
                                           configuration: Configuration
                                           )(implicit ec: ExecutionContext) extends BackendController(controllerComponents) {

  val logger = Logger("SubmissionStatusController")
  lazy val crypto = new ApplicationCrypto(configuration.underlying).JsonCrypto

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

  private def saveSubmission(reportStatus: ReportStatus, upsert: Boolean): Future[Either[Result, Unit]] = {
    logger.debug(s"Save submission ${reportStatus.redacted} upsert $upsert")
    submissionStatusRepository.saveOrUpdate(reportStatus, upsert).map(_.fold(
      _ => Left(InternalServerError),
      _ => Right(())
    ))
  }

  private def saveSubmissionUserInfo(userId: String, reference: String)
    : Future[Either[Result, Unit]] = {
    submissionStatusRepository.saveOrUpdate(userId, reference).map(_.fold(
      _ => Left(InternalServerError),
      _ => Right(())
    ))
  }

  def save(upsert: Boolean = false) = Action.async(parse.tolerantJson) { request =>
    val headers = request.headers

    logger.info(s"Saving submission upsert $upsert")

    (for {
        baCode <- EitherT.fromEither[Future](headers.get("BA-Code").toRight(Unauthorized("BA-Code missing")))
        encryptedPassword <- EitherT.fromEither[Future](headers.get("password").toRight(Unauthorized("password missing")))
        password <- EitherT.fromEither[Future](decryptPassword(encryptedPassword))
        reportStatus <- EitherT.fromEither[Future](parseReportStatus(request))
        _ <- EitherT(saveSubmission(reportStatus.copy(baCode = Some(baCode)), upsert))
        _ = webBarsService.newSubmission(reportStatus, baCode, password)
    } yield NoContent)
    .valueOr(ex => InternalServerError)
  }

  def saveUserInfo() = Action.async(parse.tolerantJson) { request =>
    (for {
      reportStatus <- EitherT.fromEither[Future](parseReportStatus(request))
      _ <- EitherT(saveSubmissionUserInfo(reportStatus.baCode.get, reportStatus.id))
    } yield NoContent)
      .valueOr(_ => InternalServerError)
  }

  private def decryptPassword(encryptedPassword: String): Either[Result, String] = {
    Try {
      crypto.decrypt(Crypted(encryptedPassword))
    } match {
      case Success(password) => Right(password.value)
      case Failure(exception) => Left(Unauthorized("Unable to decrypt password"))
    }
  }

  private def deleteByReferenceQuery(reference: String, user: String): Future[Either[Result, JsValue]] = {
    submissionStatusRepository.deleteByReference(reference, user).map { deleteResult =>
      deleteResult.left.map { error =>
        InternalServerError
      }
    }
  }

  def deleteByReference(reference: String) = Action.async { implicit request =>
    (for {
      baCode <- EitherT.fromOption[Future](request.headers.toMap.get("BA-Code").flatMap(_.headOption), Unauthorized("BA-Code missing"))
      reportStatuses <- EitherT(deleteByReferenceQuery(reference, baCode))
    } yield (Ok(Json.toJson(reportStatuses))))
      .valueOr(x => x)
  }
}
