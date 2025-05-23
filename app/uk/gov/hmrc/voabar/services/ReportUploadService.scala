/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.voabar.services

import cats.data.EitherT
import cats.implicits.*
import ebars.xml.BAreports
import models.Purpose
import play.api.Logging
import services.EbarsValidator
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.voabar.connectors.{EmailConnector, UpscanConnector, VoaBarAuditConnector, VoaEbarsConnector}
import uk.gov.hmrc.voabar.models.*
import uk.gov.hmrc.voabar.models.EbarsRequests.BAReportRequest
import uk.gov.hmrc.voabar.repositories.SubmissionStatusRepository
import uk.gov.hmrc.voabar.util.ErrorCode.*

import java.io.ByteArrayInputStream
import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneOffset}
import java.util.concurrent.TimeUnit.SECONDS
import javax.inject.Inject
import javax.xml.transform.stream.StreamSource
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Success, Try}

class ReportUploadService @Inject() (
  statusRepository: SubmissionStatusRepository,
  validationService: ValidationService,
  voaEbarsConnector: VoaEbarsConnector,
  emailConnector: EmailConnector,
  upscanConnector: UpscanConnector,
  audit: VoaBarAuditConnector
)(implicit executionContext: ExecutionContext
) extends Logging {

  val ebarsValidator  = new EbarsValidator
  val voaBarValidator = new XmlValidator

  def upload(baLogin: LoginDetails, xmlUrl: String, uploadReference: String)(implicit headerCarrier: HeaderCarrier): Future[String] = {

    val processingResult = for {
      submissions <- downloadAndFixXml(xmlUrl)
      _           <- EitherT.fromEither[Future](validationService.validate(submissions, baLogin)) // Business validation
      _           <- EitherT.fromEither[Future](voaBarValidator.validateAsDomAgainstSchema(submissions)) // XML Schema validation
      status      <- EitherT.right[BarError](upload(baLogin, submissions, uploadReference))
    } yield status

    handleUploadResult(processingResult.value, baLogin, uploadReference)

  }

  def upload(baLogin: LoginDetails, baReports: BAreports, uploadReference: String)(implicit headerCarrier: HeaderCarrier): Future[String] = {
    val processingResult = for {
      _ <- EitherT(ebarsUpload(baReports, baLogin, uploadReference))
      _  = audit.successfulReportUploaded(baLogin.username, baReports.getBApropertyReport.size())
      _ <- EitherT(statusRepository.update(uploadReference, Done, baReports.getBApropertyReport.size()))
      _ <- EitherT(sendConfirmationEmail(uploadReference, baLogin))
    } yield "ok"

    handleUploadResult(processingResult.value, baLogin, uploadReference)
  }

  def handleUploadResult(result: Future[Either[BarError, String]], login: LoginDetails, uploadReference: String)(implicit headerCarrier: HeaderCarrier)
    : Future[String] =
    result
      .recover {
        case exception: Exception =>
          logger.warn("Unexpected error when processing file, trying to recover", exception)
          Left(UnknownError(exception.getMessage))
      }
      .map { res =>
        logger.debug(s"Upload result: $res")
        SECONDS.sleep(1)
        res
      }
      .flatMap {
        case Right(v)       => Future.successful(v)
        case Left(barError) =>
          audit.reportUploadFailed(login.username, barError)
          handleError(uploadReference, barError, login)
            .map(_ => "failed")
      }

  def downloadAndFixXml(url: String)(implicit hc: HeaderCarrier): EitherT[Future, BarError, BAreports] = {
    val correctionEngine = new RulesCorrectionEngine

    def parseXml(rawXml: Array[Byte]): Either[BarError, BAreports] =
      for {
        _         <- voaBarValidator.validateInputXmlForXEE(CorrectionInputStream(new ByteArrayInputStream(rawXml)))
        baReports <- unmarshal(rawXml)
      } yield baReports

    def unmarshal(rawXml: Array[Byte]): Either[BarError, BAreports] = Try {
      val source = new StreamSource(CorrectionInputStream(new ByteArrayInputStream(rawXml)))
      ebarsValidator.fromXml(source)
    }.toEither.leftMap { e =>
      logger.warn("Unable to unmarshal XML", e)
      BarXmlError(Option(e.getMessage).getOrElse("Unable to read XML"))
    }

    def fixXml(submission: BAreports): Either[BarError, BAreports] = Try {

      val allReports = ebarsValidator.split(submission)

      allReports.foreach { report =>
        correctionEngine.applyRules(report)
      }

      submission.getBApropertyReport.clear()

      submission.getBApropertyReport.addAll(allReports.map(_.getBApropertyReport.get(0)).toList.asJava)

      FixHeader(submission)
      FixCTaxTrailer(submission)

      submission

    }.toEither.leftMap { e =>
      logger.warn("Unable to automatically fix XML", e)
      UnknownError("Unable to process upload")
    }

    for {
      rawXml     <- EitherT(upscanConnector.downloadReport(url))
      submission <- EitherT.fromEither[Future](parseXml(rawXml))
      xml        <- EitherT.fromEither[Future](fixXml(submission))
    } yield xml

  }

  private def sendConfirmationEmail(
    reportStatus: ReportStatus,
    login: LoginDetails
  ): Future[Either[BarEmailError, Unit]] =
    emailConnector.sendEmail(
      reportStatus.baCode,
      Purpose.CT, // Note: This will need to be dynamic when NDR processing is added to the service
      reportStatus.id,
      login.username,
      login.password,
      reportStatus.filename.getOrElse("filename unavailable"),
      reportStatus.createdAt.atZone(ZoneOffset.UTC).format(DateTimeFormatter.ISO_DATE_TIME),
      reportStatus.errors.map(e => s"${e.code}: ${e.values.mkString("\n")}").mkString("\n")
    )
      .map(_ => Right(()))
      .recover {
        case ex: Throwable =>
          val errorMsg = "Error while sending confirmation message"
          logger.error(errorMsg, ex)
          Left(BarEmailError(ex.getMessage))
      }

  private def sendConfirmationEmail(
    baRef: String,
    login: LoginDetails
  ): Future[Either[BarError, Unit]] =
    statusRepository.getByReference(baRef).flatMap(_.fold(
      e => {
        val errorMsg = s"Error while retrieving report to be send via email. $e"
        logger.error(errorMsg)
        Future.successful(Right(()))
      },
      reportStatus => sendConfirmationEmail(reportStatus, login)
    ))

  private def handleError(submissionId: String, barError: BarError, login: LoginDetails): Future[?] = {
    logger.warn(s"handling error, submissionID: $submissionId, Error: $barError")

    def handleValidationErrors(errors: List[Error]) =
      statusRepository.addErrors(submissionId, errors).flatMap { _ =>
        statusRepository.updateStatus(submissionId, Failed)
          .map(_ => sendConfirmationEmail(submissionId, login))
      }

    barError match {
      case BarXmlError(message) =>
        statusRepository.addError(submissionId, Error(INVALID_XML, Seq(message))).flatMap { r =>
          logger.warn(s"Mongo write result : $r")
          statusRepository.updateStatus(submissionId, Failed)
            .map(_ => sendConfirmationEmail(submissionId, login))
        }

      case BarXmlValidationError(errors) => handleValidationErrors(errors)

      case BarValidationError(errors) => handleValidationErrors(errors)

      case BarSubmissionValidationError(errors) =>
        statusRepository.saveOrUpdate(
          ReportStatus(
            id = submissionId,
            createdAt = Instant.now,
            baCode = login.username,
            reportErrors = errors,
            status = Failed.value
          ),
          upsert = true
        )

      case BarEbarError(ebarError) =>
        statusRepository.addError(submissionId, Error(EBARS_UNAVAILABLE, Seq(ebarError))).flatMap { _ =>
          statusRepository.updateStatus(submissionId, Failed)
            .map(_ => sendConfirmationEmail(submissionId, login))
        }

      case BarMongoError(error) =>
        logger.warn(s"Mongo exception, unable to update status of submission, submissionId: $submissionId. $error")
        Future.unit

      case BarEmailError(emailError) =>
        statusRepository.addError(submissionId, Error(UNKNOWN_ERROR, Seq(emailError))).flatMap { _ =>
          statusRepository.updateStatus(submissionId, Done)
        }

      case UnknownError(detail) =>
        statusRepository.addError(submissionId, Error(UNKNOWN_ERROR, Seq(detail))).flatMap { _ =>
          statusRepository.updateStatus(submissionId, Failed)
            .map(_ => sendConfirmationEmail(submissionId, login))
        }
    }
  }

  private def ebarsUpload(baReports: BAreports, login: LoginDetails, submissionId: String)(implicit headerCarrier: HeaderCarrier)
    : Future[Either[BarError, Boolean]] = {

    val riskyConversion: Either[BarError, String] = Try {
      ebarsValidator.toJson(baReports)
    } match {
      case Success(jsonString) => Right(jsonString)
      case Failure(exception)  => Left(BarEbarError(exception.getMessage))
    }

    def internalUpload(jsonString: String): Future[Either[BarError, Boolean]] = {
      val req = BAReportRequest(submissionId, jsonString, login.username, login.password)
      voaEbarsConnector.sendBAReport(req).map(_ => Right(true)).recover {
        case ex: Exception => Left(BarEbarError(ex.getMessage))
      }
    }

    val result = for {
      jsonString <- EitherT.fromEither[Future](riskyConversion)
      res        <- EitherT(internalUpload(jsonString))
    } yield res

    result.value
  }

}
