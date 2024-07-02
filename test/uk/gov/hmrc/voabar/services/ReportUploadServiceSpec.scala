/*
 * Copyright 2024 HM Revenue & Customs
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

import java.net.URL
import java.nio.file.Paths
import ebars.xml.BAreports
import models.Purpose.Purpose
import org.mockito.ArgumentMatchers.{any, same}
import org.mockito.Mockito.{times, verify, verifyNoInteractions, when}
import jakarta.xml.bind.JAXBContext
import org.apache.commons.io.IOUtils
import org.scalatest.OptionValues
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.WsScalaTestClient
import uk.gov.hmrc.voabar.repositories.SubmissionStatusRepository

import scala.concurrent.{ExecutionContext, Future}
import org.scalatest.matchers.must
import org.scalatest.wordspec.AsyncWordSpec
import play.api.http.Status.OK
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.voabar.connectors.{EmailConnector, UpscanConnector, VoaBarAuditConnector, VoaEbarsConnector}
import uk.gov.hmrc.voabar.models.EbarsRequests.BAReportRequest
import uk.gov.hmrc.voabar.models.*
import uk.gov.hmrc.voabar.util.ErrorCode.{ATLEAST_ONE_PROPOSED, CHARACTER, INVALID_XML}

import scala.util.Try

class ReportUploadServiceSpec extends AsyncWordSpec with MockitoSugar with must.Matchers with OptionValues with WsScalaTestClient {

  val uploadReference = "submissionID"

  val aXmlUrl = getClass.getResource("/xml/CTValid1.xml").toString

  implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

  val loginDetails = LoginDetails("BA5090", "BA5090")


  "ReportUploadServiceSpec" must {
    "proces request " in {
      val reportUploadService = new ReportUploadService(aCorrectStatusRepository(), aValidationService(),  aVoaEbarsConnector(),
        aEmailConnector(), aUpscanConnector(), aAuditConnector())
      val res = reportUploadService.upload(loginDetails, aXmlUrl, uploadReference)
      res.map { result =>
        result mustBe "ok"
      }
    }

    "proces request for jaxbInput " in {
      val reportUploadService = new ReportUploadService(aCorrectStatusRepository(), aValidationService(),  aVoaEbarsConnector(),
        aEmailConnector(), aUpscanConnector(), aAuditConnector())
      val jaxbInput = aJaxbInput(getClass.getResource("/xml/CTValid1.xml"))
      val res = reportUploadService.upload(loginDetails, jaxbInput, uploadReference)
      res.map { result =>
        result mustBe "ok"
      }
    }

    "record error for not valid XML" in {

      val statusRepository = aCorrectStatusRepository()

      val reportUploadService = new ReportUploadService(statusRepository, aValidationThrowError(),  aVoaEbarsConnector(),
        aEmailConnector(), aUpscanConnector(), aAuditConnector())
      val res = reportUploadService.upload(loginDetails, aXmlUrl, uploadReference)

      res.map { result =>
        verify(statusRepository).updateStatus(same(uploadReference), same(Failed))
        result mustBe "failed"
      }

    }

    "stop any work after update error" ignore { //Status update removed, we record only final status or error
      val statusRepository = mock[SubmissionStatusRepository]
      when(statusRepository.updateStatus(any[String], any[ReportStatusType]))
        .thenReturn(Future.successful(Left(BarMongoError("mongo is broken"))))
      val validationService = aValidationService()
      val voaEbarsConnector = aVoaEbarsConnector()
      val xmlParser = mock[XmlParser]

      val reportUploadService = new ReportUploadService(statusRepository, validationService,  voaEbarsConnector,
        aEmailConnector(), aUpscanConnector(), aAuditConnector())
      val res = reportUploadService.upload(loginDetails, aXmlUrl, uploadReference)
      res.map { result =>
        verify(statusRepository, times(1)).updateStatus(same(uploadReference), same(Pending))
        verifyNoInteractions(validationService, voaEbarsConnector, xmlParser)
        result mustBe "failed"
      }
    }

    "handle full XML" in {
      val fullXmlUrl = Paths.get("test/resources/xml/CTValid2.xml").toAbsolutePath.toUri.toURL.toString
      val reportUploadService = new ReportUploadService(aCorrectStatusRepository(), aValidationService(),  aVoaEbarsConnector(),
        aEmailConnector(), aUpscanConnector(), aAuditConnector())
      val res = reportUploadService.upload(loginDetails, fullXmlUrl, uploadReference)
      res.map { result =>
        result mustBe "ok"
      }
    }


    "Send submissions in a Single submission file" in {
      val baReport = Paths.get("test/resources/xml/CTValid2.xml").toAbsolutePath.toUri.toURL.toString
      val voaEbarsConnector = aVoaEbarsConnector()

      val reportUploadService = new ReportUploadService(aCorrectStatusRepository(), aValidationService(),  voaEbarsConnector,
        aEmailConnector(), aUpscanConnector(), aAuditConnector())
      val res = reportUploadService.upload(loginDetails, baReport, uploadReference)

      res.map { result =>
        verify(voaEbarsConnector, times(1)).sendBAReport(any[BAReportRequest])(any[ExecutionContext], any[HeaderCarrier])
        result mustBe "ok"
      }
    }
  }


  "Error handler" should {

    "persis BarXmlError" in {

      val validationService = mock[ValidationService]
      when(validationService.validate(any[BAreports], any[LoginDetails])).thenReturn(Left(BarXmlError("validation error")))
      val statusRepository = aCorrectStatusRepository()
      val reportUploadService = new ReportUploadService(statusRepository, validationService,  aVoaEbarsConnector(),
        aEmailConnector(), aUpscanConnector(), aAuditConnector())
      val result = reportUploadService.upload(loginDetails, aXmlUrl, "reference1")

      result.map { value =>
        verify(statusRepository, times(1)).addError("reference1", Error(INVALID_XML, Seq("validation error")))
        value mustBe "failed"
      }
    }


    "persis all Error from BarXmlValidationError" in {

      val errors = List (
        Error(CHARACTER),
        Error(ATLEAST_ONE_PROPOSED)
      )
      val xmlValidationError = BarXmlValidationError(errors)

      val validationService = mock[ValidationService]
      when(validationService.validate(any[BAreports], any[LoginDetails])).thenReturn(Left(xmlValidationError))
      val statusRepository = aCorrectStatusRepository()
      val reportUploadService = new ReportUploadService(statusRepository, validationService,  aVoaEbarsConnector(),
        aEmailConnector(), aUpscanConnector(), aAuditConnector())
      val result = reportUploadService.upload(loginDetails, aXmlUrl, "reference1")


      result.map { value =>
        verify(statusRepository, times(1)).addErrors("reference1", errors)
//        verify(statusRepository, times(1)).addError("reference1", Error(CHARACTER, Seq()))
//        verify(statusRepository, times(1)).addError("reference1", Error(ATLEAST_ONE_PROPOSED, Seq()))
        value mustBe "failed"
      }

    }

    "handle email Submission error" in {

      val emailConnector = mock[EmailConnector]
      when(emailConnector.sendEmail(any[String], any[Purpose], any[String], any[String], any[String], any[String], any[String], any[String])).thenReturn {
        Future.failed(new RuntimeException("email sending failed"))
      }

      val reportUploadService = new ReportUploadService(aCorrectStatusRepository(), aValidationService(),  aVoaEbarsConnector(),
        emailConnector, aUpscanConnector(), aAuditConnector())
      val res = reportUploadService.upload(loginDetails, aXmlUrl, uploadReference)
      res.map { result =>
        result mustBe "failed"
      }
    }

    "handle eBar Error error" in {

      val voaEbarsConnector = mock[VoaEbarsConnector]
      when(voaEbarsConnector.sendBAReport(any[BAReportRequest])(any[ExecutionContext], any[HeaderCarrier])).thenReturn {
        Future.failed(new RuntimeException("Can't send data to ebars."))
      }

      val reportUploadService = new ReportUploadService(aCorrectStatusRepository(), aValidationService(),  voaEbarsConnector,
        aEmailConnector(), aUpscanConnector(), aAuditConnector())
      val res = reportUploadService.upload(loginDetails, aXmlUrl, uploadReference)
      res.map { result =>
        result mustBe "failed"
      }
    }

  }

  def aCorrectStatusRepository(): SubmissionStatusRepository = {
    val repository = mock[SubmissionStatusRepository]
    val reportStatus = ReportStatus("submissionId", baCode = "BA1010", filename = Some("filename.xml"), status = Some(Pending.value))

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
  }

  def aJaxbInput(xml: URL) = {
    val doc = aXmlParser().parse(xml).toOption.get
    val jaxbContext = JAXBContext.newInstance("ebars.xml")
    val xmlUnmarshaller = jaxbContext.createUnmarshaller()
    xmlUnmarshaller.unmarshal(doc).asInstanceOf[BAreports]
  }

  def aValidationService(): ValidationService = {
    val validationService = mock[ValidationService]
    when(validationService.validate(any[BAreports], any[LoginDetails])).thenReturn(Right(()))
    validationService
  }

  def aValidationThrowError() = {
    val validationService = mock[ValidationService]
    when(validationService.validate(any[BAreports], any[LoginDetails])).thenReturn(Left(BarXmlError("Failed")))
    validationService
  }

  def aXmlParser(): XmlParser = {
    new XmlParser()
  }

  def aSubmissionProcessingService(): V1ValidationService = {
    val processingEngine = mock[V1ValidationService]
    when(processingEngine.fixAndValidateAsV2(any[Array[Byte]], any[String], any[String], any[String])).thenReturn(true)
    processingEngine
  }

  def aVoaEbarsConnector(): VoaEbarsConnector = {
    val connector = mock[VoaEbarsConnector]

    when(connector.sendBAReport(any[BAReportRequest])(any[ExecutionContext], any[HeaderCarrier]))
      .thenAnswer(_ => Future.successful(OK))

    when(connector.validate(any[LoginDetails])(any[ExecutionContext], any[HeaderCarrier]))
      .thenAnswer(_ => Future.successful(Try(OK)))

    connector
  }

  def aEmailConnector(): EmailConnector = {
    val emailConnector = mock[EmailConnector]

    when(emailConnector.sendEmail(any[String], any[Purpose], any[String], any[String], any[String], any[String], any[String], any[String]))
      .thenAnswer(_ => Future.unit)

    emailConnector
  }

  def aUpscanConnector() = {
    new UpscanConnector {
      override def downloadReport(url: String)(implicit hc: HeaderCarrier): Future[Either[BarError, Array[Byte]]] = {
        Future(Right(IOUtils.toByteArray(new URL(url).openStream())))
      }
    }
  }

  def aAuditConnector() = {
    val hmrcAudit = mock[AuditConnector]
    new VoaBarAuditConnector(hmrcAudit)
  }

}
