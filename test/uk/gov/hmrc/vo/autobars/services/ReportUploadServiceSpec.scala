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

package uk.gov.hmrc.vo.autobars.services

import ebars.xml.BAreports
import jakarta.xml.bind.JAXBContext
import models.Purpose
import org.apache.commons.io.IOUtils
import org.mockito.ArgumentMatchers.same
import org.w3c.dom.Document
import play.api.http.Status.OK
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.vo.autobars.connectors.{EmailConnector, UpscanConnector, VOBarAuditConnector, VOEbarsConnector}
import uk.gov.hmrc.vo.autobars.models.*
import uk.gov.hmrc.vo.autobars.models.EbarsRequests.BAReportRequest
import uk.gov.hmrc.vo.autobars.repositories.SubmissionStatusRepository
import uk.gov.hmrc.vo.autobars.util.ErrorCode.{ATLEAST_ONE_PROPOSED, CHARACTER, INVALID_XML}
import uk.gov.hmrc.vo.unit.test.BaseSpec

import java.net.{URI, URL}
import java.nio.file.Paths
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class ReportUploadServiceSpec extends BaseSpec:

  private val uploadReference = "submissionID"

  private val aXmlUrl = getClass.getResource("/xml/CTValid1.xml").toString

  implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

  private val loginDetails = LoginDetails("BA5090", "BA5090")

  "ReportUploadService" should {
    "process request" in {
      val reportUploadService =
        ReportUploadService(aCorrectStatusRepository(), aValidationService(), aVOEbarsConnector(), aEmailConnector(), aUpscanConnector(), aAuditConnector())
      val res                 = reportUploadService.upload(loginDetails, aXmlUrl, uploadReference)
      res.futureValue shouldBe "ok"
    }

    "process request for jaxbInput " in {
      val reportUploadService =
        ReportUploadService(aCorrectStatusRepository(), aValidationService(), aVOEbarsConnector(), aEmailConnector(), aUpscanConnector(), aAuditConnector())
      val jaxbInput           = aJaxbInput(getClass.getResource("/xml/CTValid1.xml"))
      val res                 = reportUploadService.upload(loginDetails, jaxbInput, uploadReference)
      res.futureValue shouldBe "ok"
    }

    "record error for not valid XML" in {
      val statusRepository = aCorrectStatusRepository()

      val reportUploadService =
        ReportUploadService(statusRepository, aValidationThrowError(), aVOEbarsConnector(), aEmailConnector(), aUpscanConnector(), aAuditConnector())
      val res                 = reportUploadService.upload(loginDetails, aXmlUrl, uploadReference)

      res.map { result =>
        verify(statusRepository).updateStatus(same(uploadReference), same(Failed))
        result shouldBe "failed"
      }
    }

    "stop any work after update error" ignore { // Status update removed, we record only final status or error
      val statusRepository  = mock[SubmissionStatusRepository]
      when(statusRepository.updateStatus(any[String], any[ReportStatusType]))
        .thenReturn(Future.successful(Left(BarMongoError("mongo is broken"))))
      val validationService = aValidationService()
      val voEbarsConnector  = aVOEbarsConnector()
      val xmlParser         = mock[XmlParser]

      val reportUploadService =
        ReportUploadService(statusRepository, validationService, voEbarsConnector, aEmailConnector(), aUpscanConnector(), aAuditConnector())
      val res                 = reportUploadService.upload(loginDetails, aXmlUrl, uploadReference)
      res.map { result =>
        verify(statusRepository, times(1)).updateStatus(same(uploadReference), same(Pending))
        verifyNoInteractions(validationService, voEbarsConnector, xmlParser)
        result shouldBe "failed"
      }
    }

    "handle full XML" in {
      val fullXmlUrl          = Paths.get("test/resources/xml/CTValid2.xml").toAbsolutePath.toUri.toURL.toString
      val reportUploadService =
        ReportUploadService(aCorrectStatusRepository(), aValidationService(), aVOEbarsConnector(), aEmailConnector(), aUpscanConnector(), aAuditConnector())
      val res                 = reportUploadService.upload(loginDetails, fullXmlUrl, uploadReference)
      res.futureValue shouldBe "ok"
    }

    "send submissions in a Single submission file" in {
      val baReport         = Paths.get("test/resources/xml/CTValid2.xml").toAbsolutePath.toUri.toURL.toString
      val voEbarsConnector = aVOEbarsConnector()

      val reportUploadService =
        ReportUploadService(aCorrectStatusRepository(), aValidationService(), voEbarsConnector, aEmailConnector(), aUpscanConnector(), aAuditConnector())
      val res                 = reportUploadService.upload(loginDetails, baReport, uploadReference)

      res.map { result =>
        verify(voEbarsConnector, times(1)).sendBAReport(any[BAReportRequest])(using any[ExecutionContext], any[HeaderCarrier])
        result shouldBe "ok"
      }
    }
  }

  "Error handler" should {
    "persist BarXmlError" in {
      val validationService   = mock[ValidationService]
      when(validationService.validate(any[BAreports], any[LoginDetails])).thenReturn(Left(BarXmlError("validation error")))
      val statusRepository    = aCorrectStatusRepository()
      val reportUploadService =
        ReportUploadService(statusRepository, validationService, aVOEbarsConnector(), aEmailConnector(), aUpscanConnector(), aAuditConnector())
      val result              = reportUploadService.upload(loginDetails, aXmlUrl, "reference1")

      result.map { value =>
        verify(statusRepository, times(1)).addError("reference1", Error(INVALID_XML, Seq("validation error")))
        value shouldBe "failed"
      }
    }

    "persist all Error from BarXmlValidationError" in {
      val errors             = List(
        Error(CHARACTER),
        Error(ATLEAST_ONE_PROPOSED)
      )
      val xmlValidationError = BarXmlValidationError(errors)

      val validationService   = mock[ValidationService]
      when(validationService.validate(any[BAreports], any[LoginDetails])).thenReturn(Left(xmlValidationError))
      val statusRepository    = aCorrectStatusRepository()
      val reportUploadService =
        ReportUploadService(statusRepository, validationService, aVOEbarsConnector(), aEmailConnector(), aUpscanConnector(), aAuditConnector())
      val result              = reportUploadService.upload(loginDetails, aXmlUrl, "reference1")

      result.map { value =>
        verify(statusRepository, times(1)).addErrors("reference1", errors)
        value shouldBe "failed"
      }
    }

    "handle email Submission error" in {
      val emailConnector = mock[EmailConnector]
      when(emailConnector.sendEmail(any[String], any[Purpose], any[String], any[String], any[String], any[String], any[String], any[String])).thenReturn {
        Future.failed(RuntimeException("email sending failed"))
      }

      val reportUploadService =
        ReportUploadService(aCorrectStatusRepository(), aValidationService(), aVOEbarsConnector(), emailConnector, aUpscanConnector(), aAuditConnector())
      val res                 = reportUploadService.upload(loginDetails, aXmlUrl, uploadReference)
      res.futureValue shouldBe "failed"
    }

    "handle eBar Error error" in {
      val voEbarsConnector = mock[VOEbarsConnector]
      when(voEbarsConnector.sendBAReport(any[BAReportRequest])(using any[ExecutionContext], any[HeaderCarrier])).thenReturn {
        Future.failed(RuntimeException("Can't send data to ebars."))
      }

      val reportUploadService =
        ReportUploadService(aCorrectStatusRepository(), aValidationService(), voEbarsConnector, aEmailConnector(), aUpscanConnector(), aAuditConnector())
      val res                 = reportUploadService.upload(loginDetails, aXmlUrl, uploadReference)
      res.futureValue shouldBe "failed"
    }
  }

  private def aCorrectStatusRepository(): SubmissionStatusRepository =
    val repository   = mock[SubmissionStatusRepository]
    val reportStatus = ReportStatus("submissionId", baCode = "BA1010", filename = Some("filename.xml"), status = Pending.value)
    when(repository.updateStatus(any[String], any[ReportStatusType]))
      .thenAnswer(_ => Future.successful(Right(true)))
    when(repository.update(any[String], any[ReportStatusType], any[Int]))
      .thenAnswer(_ => Future.successful(Right(true)))
    when(repository.addError(any[String], any[Error]))
      .thenAnswer(_ => Future.successful(Right(true)))
    when(repository.addErrors(any[String], any))
      .thenAnswer(_ => Future.successful(Right(true)))
    when(repository.getByReference(any[String]))
      .thenAnswer(_ => Future.successful(Right(reportStatus)))
    repository

  private def aJaxbInput(xml: URL): BAreports =
    val doc: Document   = aXmlParser().parse(xml).fold(err => throw Exception(err.toString), identity)
    val jaxbContext     = JAXBContext.newInstance("ebars.xml")
    val xmlUnmarshaller = jaxbContext.createUnmarshaller()
    xmlUnmarshaller.unmarshal(doc).asInstanceOf[BAreports]

  private def aValidationService(): ValidationService =
    val validationService = mock[ValidationService]
    when(validationService.validate(any[BAreports], any[LoginDetails])).thenReturn(Right(()))
    validationService

  private def aValidationThrowError() =
    val validationService = mock[ValidationService]
    when(validationService.validate(any[BAreports], any[LoginDetails])).thenReturn(Left(BarXmlError("Failed")))
    validationService

  private def aXmlParser(): XmlParser = XmlParser()

  private def aVOEbarsConnector(): VOEbarsConnector =
    val connector = mock[VOEbarsConnector]
    when(connector.sendBAReport(any[BAReportRequest])(using any[ExecutionContext], any[HeaderCarrier]))
      .thenAnswer(_ => Future.successful(OK))
    when(connector.validate(any[LoginDetails]))
      .thenAnswer(_ => Future.successful(Try(OK)))
    connector

  private def aEmailConnector(): EmailConnector =
    val emailConnector = mock[EmailConnector]
    when(emailConnector.sendEmail(any[String], any[Purpose], any[String], any[String], any[String], any[String], any[String], any[String]))
      .thenAnswer(_ => Future.unit)
    emailConnector

  private def aUpscanConnector() =
    new UpscanConnector:
      override def downloadReport(url: String)(using hc: HeaderCarrier): Future[Either[BarError, Array[Byte]]] =
        Future.successful(Right(IOUtils.toByteArray(URI(url).toURL.openStream())))

  private def aAuditConnector() =
    val hmrcAudit = mock[AuditConnector]
    VOBarAuditConnector(hmrcAudit)
