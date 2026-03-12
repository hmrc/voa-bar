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

package uk.gov.hmrc.vo.autobars.controllers

import play.api.Configuration
import play.api.libs.json.JsValue
import play.api.mvc.{Action, MessagesControllerComponents, Request, Result}
import uk.gov.hmrc.crypto.{ApplicationCrypto, Crypted}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.vo.autobars.exception.VOBarException
import uk.gov.hmrc.vo.autobars.models.{Error, Failed, LoginDetails, ReportStatus, ReportStatusType, Submitted}
import uk.gov.hmrc.vo.autobars.models.UpScanRequests.*
import uk.gov.hmrc.vo.autobars.repositories.{SubmissionStatusRepository, UserReportUploadsRepository}
import uk.gov.hmrc.vo.autobars.services.{ReportUploadService, WebBarsService}
import uk.gov.hmrc.vo.autobars.util.ErrorCode.{TIMEOUT_ERROR, UNKNOWN_ERROR, UPSCAN_ERROR}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class UpscanCallbackController @Inject() (
  configuration: Configuration,
  reportUploadService: ReportUploadService,
  userReportUploadsRepository: UserReportUploadsRepository,
  submissionStatusRepository: SubmissionStatusRepository,
  webBarsService: WebBarsService,
  controllerComponents: MessagesControllerComponents
)(using val ec: ExecutionContext
) extends BackendController(controllerComponents)
  with FunctionalRun {

  private val crypto = ApplicationCrypto(configuration.underlying).JsonCrypto

  def onConfirmation(baLogin: String): Action[JsValue] = Action(parse.tolerantJson) { implicit request: Request[JsValue] =>
    (parseUploadConfirmation(request).map(onSuccessfulConfirmation(baLogin, _)) orElse
      parseUploadConfirmationError(request).map(onFailedConfirmation(baLogin, _)))
      .fold {
        logger.warn(s"Couldn't parse upload confirmation: \n${request.body}")
        BadRequest("Unable to parse request")
      }(_ => Accepted)
  }

  private def parseUploadConfirmation(request: Request[JsValue]): Option[UploadConfirmation] =
    request.body.validate[UploadConfirmation].asOpt

  private def parseUploadConfirmationError(request: Request[JsValue]): Option[UploadConfirmationError] =
    request.body.validate[UploadConfirmationError].asOpt

  private def getLoginDetails(id: String): F[LoginDetails] =
    for {
      userReportUploadOpt <- fromFuture(userReportUploadsRepository.findById(id))
      userData            <- F.fromOption(userReportUploadOpt, VOBarException(Error(TIMEOUT_ERROR, Seq(s"Couldn't get user session for reference: $id"))))
      decryptedPassword   <- F.fromTry(Try(crypto.decrypt(Crypted(userData.userPassword)).value))
    } yield LoginDetails(userData.userId, decryptedPassword)

  private def sendContent(
    login: LoginDetails,
    xmlUrl: String,
    uploadConfirmation: UploadConfirmation
  )(using hc: HeaderCarrier
  ): F[String] =
    fromFuture(
      reportUploadService.upload(login, xmlUrl, uploadConfirmation.reference)
        .map { uploadResult =>
          logger.info(s"uploadResult: $uploadResult reference: ${uploadConfirmation.reference}")
          uploadResult
        }
    )

  private def saveSubmission(login: LoginDetails, reportStatus: ReportStatus): F[Unit] =
    for {
      _ <- fromFuture(submissionStatusRepository.saveOrUpdate(reportStatus, upsert = true))
    } yield webBarsService.newSubmission(reportStatus, login.username, login.password)

  private def saveReportStatus(
    login: LoginDetails,
    uploadConfirmation: UploadConfirmation,
    errors: Seq[Error] = Seq(),
    status: ReportStatusType
  ): F[Unit] = {
    val reportStatus = ReportStatus(
      uploadConfirmation.reference,
      baCode = login.username,
      url = Some(uploadConfirmation.downloadUrl),
      checksum = Some(uploadConfirmation.uploadDetails.checksum),
      status = status.value,
      filename = Some(uploadConfirmation.uploadDetails.fileName),
      errors = errors
    )
    saveSubmission(login, reportStatus)
  }

  private def saveReportStatus(
    login: LoginDetails,
    reference: String,
    errors: Seq[Error],
    status: ReportStatusType
  ): F[Unit] = {
    val reportStatus = ReportStatus(
      reference,
      baCode = login.username,
      status = status.value,
      errors = errors
    )
    saveSubmission(login, reportStatus)
  }

  private def onSuccessfulConfirmation(baLogin: String, uploadConfirmation: UploadConfirmation)(using request: Request[JsValue]): Future[Result] =
    run {
      (for {
        login <- getLoginDetails(uploadConfirmation.reference)
        _     <- saveReportStatus(login, uploadConfirmation, status = Submitted)
        _     <- sendContent(login, uploadConfirmation.downloadUrl, uploadConfirmation)
      } yield NoContent)
        .recoverWith(handleError(baLogin, uploadConfirmation.reference))
    }

  private def onFailedConfirmation(baLogin: String, uploadConfirmationError: UploadConfirmationError): Future[Result] = {
    logger.warn(s"Upload failed on upscan with: $uploadConfirmationError")
    val failureDetails = uploadConfirmationError.failureDetails
    run {
      (for {
        login <- getLoginDetails(uploadConfirmationError.reference)
        _     <- saveReportStatus(
                   login,
                   uploadConfirmationError.reference,
                   Seq(Error(UPSCAN_ERROR, Seq(failureDetails.failureReason, failureDetails.message))),
                   status = Failed
                 )
      } yield NoContent)
        .recoverWith(handleError(baLogin, uploadConfirmationError.reference))
    }
  }

  private def handleError(baLogin: String, reference: String): PartialFunction[Throwable, F[Result]] = {
    case barException: VOBarException =>
      logger.warn(s"VOBarException: ${barException.error}")
      saveError(baLogin, reference, barException.error)
    case exception                    =>
      logger.warn(s"Unknown ${exception.getClass}: ${exception.getMessage}")
      saveError(baLogin, reference, Error(UNKNOWN_ERROR, Seq(exception.getMessage)))
  }

  private def saveError(baLogin: String, reference: String, error: Error): F[Result] = {
    val errorMsg = s"Error: code: ${error.code} detail messages: ${error.values.mkString(", ")}"
    logger.error(errorMsg)
    for {
      login <- getLoginDetails(reference).recover(_ => LoginDetails(baLogin, baLogin))
      _     <- saveReportStatus(login, reference, Seq(error), status = Failed)
    } yield InternalServerError(errorMsg)
  }

}
