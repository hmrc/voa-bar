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

import play.api.Configuration
import play.api.libs.json.JsValue
import play.api.mvc.{Action, MessagesControllerComponents, Request, Result}
import uk.gov.hmrc.crypto.{ApplicationCrypto, Crypted}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.voabar.exception.VoaBarException
import uk.gov.hmrc.voabar.models.UpScanRequests._
import uk.gov.hmrc.voabar.models.{Error, Failed, LoginDetails, ReportStatus, ReportStatusType, Submitted}
import uk.gov.hmrc.voabar.repositories.{SubmissionStatusRepository, UserReportUploadsRepository}
import uk.gov.hmrc.voabar.services.{ReportUploadService, WebBarsService}
import uk.gov.hmrc.voabar.util.UPSCAN_ERROR

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class UpscanCallbackController @Inject()(configuration: Configuration,
                                         reportUploadService: ReportUploadService,
                                         userReportUploadsRepository: UserReportUploadsRepository,
                                         submissionStatusRepository: SubmissionStatusRepository,
                                         webBarsService: WebBarsService,
                                         controllerComponents: MessagesControllerComponents)
                                        (implicit val ec: ExecutionContext)
  extends BackendController(controllerComponents) with FunctionalRun {

  lazy val crypto = new ApplicationCrypto(configuration.underlying).JsonCrypto

  def onConfirmation: Action[JsValue] = Action(parse.tolerantJson) { implicit request: Request[JsValue] =>
    (parseUploadConfirmation(request).map(onSuccessfulConfirmation) orElse
      parseUploadConfirmationError(request).map(onFailedConfirmation))
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
      userData <- F.fromOption(userReportUploadOpt, VoaBarException(Error(UPSCAN_ERROR, Seq(s"Couldn't get user session for reference: $id"))))
      decryptedPassword <- F.fromTry(Try(crypto.decrypt(Crypted(userData.userPassword)).value))
    } yield LoginDetails(userData.userId, decryptedPassword)

  private def sendContent(
                           login: LoginDetails,
                           xmlUrl: String,
                           uploadConfirmation: UploadConfirmation
                         )(implicit hc: HeaderCarrier): F[String] =
    fromFuture(
      reportUploadService.upload(login, xmlUrl, uploadConfirmation.reference)
        .map(uploadResult => {
          logger.info(s"uploadResult: $uploadResult reference: ${uploadConfirmation.reference}")
          uploadResult
        })
    )

  private def saveSubmission(login: LoginDetails, reportStatus: ReportStatus): F[Unit] =
    for {
      _ <- fromFuture(submissionStatusRepository.saveOrUpdate(reportStatus.copy(baCode = Some(login.username)), true))
    } yield webBarsService.newSubmission(reportStatus, login.username, login.password)

  private def saveReportStatus(
                                login: LoginDetails,
                                uploadConfirmation: UploadConfirmation,
                                errors: Seq[Error] = Seq(),
                                status: ReportStatusType
                              ): F[Unit] = {
    val reportStatus = ReportStatus(
      uploadConfirmation.reference,
      url = Some(uploadConfirmation.downloadUrl),
      checksum = Some(uploadConfirmation.uploadDetails.checksum),
      status = Some(status.value),
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
      status = Some(status.value),
      errors = errors
    )
    saveSubmission(login, reportStatus)
  }


  private def onSuccessfulConfirmation(uploadConfirmation: UploadConfirmation)(implicit request: Request[JsValue]): Future[Result] =
    run {
      (for {
        login <- getLoginDetails(uploadConfirmation.reference)
        _ <- saveReportStatus(login, uploadConfirmation, status = Submitted)
        _ <- sendContent(login, uploadConfirmation.downloadUrl, uploadConfirmation)
      } yield NoContent)
        .recoverWith {
          case voaBarException: VoaBarException =>
            logger.warn(s"VoaBarException: ${voaBarException.error}")
            handleConfirmationError(uploadConfirmation, voaBarException.error)
        }
    }

  private def onFailedConfirmation(uploadConfirmationError: UploadConfirmationError): Future[Result] = {
    logger.warn(s"Upload failed on upscan with: $uploadConfirmationError")
    run {
      for {
        login <- getLoginDetails(uploadConfirmationError.reference)
        _ <- saveReportStatus(login,
          uploadConfirmationError.reference,
          Seq(Error(UPSCAN_ERROR, Seq(uploadConfirmationError.failureDetails.failureReason))),
          status = Failed)
      } yield NoContent
    }
  }

  private def handleConfirmationError(uploadConfirmation: UploadConfirmation, error: Error): F[Result] = {
    val errorMsg = s"Error: code: ${error.code} detail messages: ${error.values.mkString(", ")}"
    logger.error(errorMsg)
    for {
      login <- getLoginDetails(uploadConfirmation.reference)
      _ <- saveReportStatus(login, uploadConfirmation.reference, Seq(error), status = Failed)
    } yield InternalServerError(errorMsg)
  }

}
