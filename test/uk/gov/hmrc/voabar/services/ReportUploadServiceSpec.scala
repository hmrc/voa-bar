/*
 * Copyright 2021 HM Revenue & Customs
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
import java.time.ZonedDateTime
import ebars.xml.BAreports

import javax.xml.bind.JAXBContext
import org.apache.commons.io.IOUtils
import org.scalatest.{AsyncWordSpec, MustMatchers, OptionValues}
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.WsScalaTestClient
import uk.gov.hmrc.voabar.repositories.SubmissionStatusRepository

import scala.concurrent.{ExecutionContext, Future}
import org.mockito.Mockito.when
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyZeroInteractions
import org.mockito.Matchers.anyString
import org.mockito.Matchers.anyObject
import org.mockito.Matchers.any
import org.mockito.Matchers.{eq => meq}
import org.mockito.Mockito
import org.mockito.Mockito.times
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.voabar.connectors.{EmailConnector, LegacyConnector, UpscanConnector, VoaBarAuditConnector}
import uk.gov.hmrc.voabar.models.EbarsRequests.BAReportRequest
import uk.gov.hmrc.voabar.models._
import uk.gov.hmrc.voabar.util.{ATLEAST_ONE_PROPOSED, CHARACTER, INVALID_XML}

import scala.util.Try

class ReportUploadServiceSpec extends AsyncWordSpec with MockitoSugar with  MustMatchers with OptionValues with WsScalaTestClient {

  val uploadReference = "submissionID"

  val aXmlUrl = getClass.getResource("/xml/CTValid1.xml").toString

  implicit val headerCarrier = HeaderCarrier()

  val loginDetails = LoginDetails("BA5090", "BA5090")


  "ReportUploadServiceSpec" must {
    "proces request " in {
      val reportUploadService = new ReportUploadService(aCorrectStatusRepository(), aValidationService(),  aLegacyConnector(), aEmailConnector(), aUpscanConnector(), aAuditConnector())
      val res = reportUploadService.upload(loginDetails, aXmlUrl, uploadReference)
      res.map { result =>
        result mustBe "ok"
      }
    }

    "proces request for jaxbInput " in {
      val reportUploadService = new ReportUploadService(aCorrectStatusRepository(), aValidationService(),  aLegacyConnector(), aEmailConnector(), aUpscanConnector(), aAuditConnector())
      val jaxbInput = aJaxbInput(getClass.getResource("/xml/CTValid1.xml"))
      val res = reportUploadService.upload(loginDetails, jaxbInput, uploadReference)
      res.map { result =>
        result mustBe "ok"
      }
    }

    "record error for not valid XML" in {

      val statusRepository = aCorrectStatusRepository()

      val reportUploadService = new ReportUploadService(statusRepository, aValidationThrowError(),  aLegacyConnector(), aEmailConnector(), aUpscanConnector(), aAuditConnector())
      val res = reportUploadService.upload(loginDetails, aXmlUrl, uploadReference)

      res.map { result =>
        verify(statusRepository).updateStatus(meq(uploadReference), meq(Failed))
        result mustBe "failed"
      }

    }

    "stop any work after update error" ignore { //Status update removed, we record only final status or error
      val statusRepository = mock[SubmissionStatusRepository]
      when(statusRepository.updateStatus(anyString(), any(classOf[ReportStatusType])))
        .thenReturn(Future.successful(Left(BarMongoError("mongo is broken", None))))
      val validationService = aValidationService()
      val legacyConnector = aLegacyConnector()
      val xmlParser = mock[XmlParser]

      val reportUploadService = new ReportUploadService(statusRepository, validationService,  legacyConnector, aEmailConnector(), aUpscanConnector(), aAuditConnector())
      val res = reportUploadService.upload(loginDetails, aXmlUrl, uploadReference)
      res.map { result =>
        verify(statusRepository, times(1)).updateStatus(meq(uploadReference), meq(Pending))
        verifyZeroInteractions(validationService, legacyConnector, xmlParser)
        result mustBe "failed"
      }
    }

    "handle full XML" in {
      val fullXmlUrl = Paths.get("test/resources/xml/CTValid2.xml").toAbsolutePath.toUri.toURL.toString
      val reportUploadService = new ReportUploadService(aCorrectStatusRepository(), aValidationService(),  aLegacyConnector(), aEmailConnector(), aUpscanConnector(), aAuditConnector())
      val res = reportUploadService.upload(loginDetails, fullXmlUrl, uploadReference)
      res.map { result =>
        result mustBe "ok"
      }
    }


    "Send submissions in a Single submission file" in {
      val baReport = Paths.get("test/resources/xml/CTValid2.xml").toAbsolutePath.toUri.toURL.toString
      val legacyConnector = aLegacyConnector()

      val reportUploadService = new ReportUploadService(aCorrectStatusRepository(), aValidationService(),  legacyConnector, aEmailConnector(), aUpscanConnector(), aAuditConnector())
      val res = reportUploadService.upload(loginDetails, baReport, uploadReference)

      res.map { result =>
        result mustBe "ok"

        val invocations = Mockito.mockingDetails(legacyConnector).getInvocations

        invocations must have size 1

      }
    }
  }


  "Error handler" should {

    "persis BarXmlError" in {

      val validationService = mock[ValidationService]
      when(validationService.validate(any[BAreports], any[LoginDetails])).thenReturn(Left(BarXmlError("validation error")))
      val statusRepository = aCorrectStatusRepository()
      val reportUploadService = new ReportUploadService(statusRepository, validationService,  aLegacyConnector(), aEmailConnector(), aUpscanConnector(), aAuditConnector())
      val resutl = reportUploadService.upload(loginDetails, aXmlUrl, "reference1")

      resutl.map { value =>
        value mustBe("failed")
        verify(statusRepository, times(1)).addError("reference1", Error(INVALID_XML, Seq("validation error")))
        true mustBe(true)
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
      val reportUploadService = new ReportUploadService(statusRepository, validationService,  aLegacyConnector(), aEmailConnector(), aUpscanConnector(), aAuditConnector())
      val resutl = reportUploadService.upload(loginDetails, aXmlUrl, "reference1")


      resutl.map { value =>
        value mustBe("failed")
        verify(statusRepository, times(1)).addError("reference1", Error(CHARACTER, Seq()))
        verify(statusRepository, times(1)).addError("reference1", Error(ATLEAST_ONE_PROPOSED, Seq()))
        true mustBe(true)
      }

    }

    "handle email Submission error" in {

      val emailConnector = mock[EmailConnector]
      when(emailConnector.sendEmail(anyString(), anyObject(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn {
        Future.failed(new RuntimeException("email sending failed"))
      }

      val reportUploadService = new ReportUploadService(aCorrectStatusRepository(), aValidationService(),  aLegacyConnector(), emailConnector, aUpscanConnector(), aAuditConnector())
      val res = reportUploadService.upload(loginDetails, aXmlUrl, uploadReference)
      res.map { result =>
        result mustBe "failed"
      }
    }

    "handle eBar Error error" in {

      val legacyConnector = mock[LegacyConnector]
      when(legacyConnector.sendBAReport(any[BAReportRequest])(any[ExecutionContext], any[HeaderCarrier])).thenReturn {
        Future.failed(new RuntimeException("Can't send data to ebars."))
      }

      val reportUploadService = new ReportUploadService(aCorrectStatusRepository(), aValidationService(),  legacyConnector, aEmailConnector(), aUpscanConnector(), aAuditConnector())
      val res = reportUploadService.upload(loginDetails, aXmlUrl, uploadReference)
      res.map { result =>
        result mustBe "failed"
      }
    }

  }

//  "SubmissionProcessingService" must {
//    "not crash ReportUploadService" in {
//      val submissionProcessingService = mock[SubmissionProcessingService]
//      when(submissionProcessingService.processAsV1(any[String], any[String], any[String], any[BarError]))
//        .thenThrow(new RuntimeException("This exception should not crash ReportUploadService"))
//
//      when(submissionProcessingService.processAsV1(any[Array[Byte]], any[String], any[String]))
//        .thenThrow(new RuntimeException("This exception should not crash ReportUploadService"))
//
//      val reportUploadService = new ReportUploadService(aCorrectStatusRepository(), aValidationThrowError(), submissionProcessingService, aLegacyConnector(), aEmailConnector(), aUpscanConnector())
//      val res = reportUploadService.upload("username", "password", aXmlUrl, uploadReference)
//
//      res.map { result =>
//        verify(submissionProcessingService, times(1)).processAsV1(any[String], any[String], any[String], any[BarError])
//        result mustBe "failed"
//      }
//
//    }
//  }

  def aCorrectStatusRepository(): SubmissionStatusRepository = {
    val repository = mock[SubmissionStatusRepository]
    val reportStatus = ReportStatus("submissionId", ZonedDateTime.now, filename = Some("filename.xml"), status = Some(Pending.value))
    when(repository.updateStatus(anyString(), any(classOf[ReportStatusType]))).thenAnswer(new Answer[Future[Either[BarError, Boolean]]] {
      override def answer(invocationOnMock: InvocationOnMock): Future[Either[BarError, Boolean]] = Future.successful(Right(true))
    })

    when(repository.update(anyString(), any(classOf[ReportStatusType]), any[Int])).thenAnswer(new Answer[Future[Either[BarError, Boolean]]] {
      override def answer(invocationOnMock: InvocationOnMock): Future[Either[BarError, Boolean]] = Future.successful(Right(true))
    })

    when(repository.addError(anyString(), any(classOf[Error]))).thenAnswer(new Answer[Future[Either[BarError, Boolean]]] {
      override def answer(invocationOnMock: InvocationOnMock): Future[Either[BarError, Boolean]] = Future.successful(Right(true))
    })

    when(repository.getByReference(anyString())).thenAnswer(new Answer[Future[Either[BarError, ReportStatus]]] {
      override def answer(invocationOnMock: InvocationOnMock): Future[Either[BarError, ReportStatus]] = Future.successful(Right(reportStatus))
    })
    repository
  }

  def aJaxbInput(xml: URL) = {
    val doc = aXmlParser().parse(xml).right.get
    val jaxbContext = JAXBContext.newInstance("ebars.xml")
    val xmlUnmarshaller = jaxbContext.createUnmarshaller()
    xmlUnmarshaller.unmarshal(doc).asInstanceOf[BAreports]
  }

  def aValidationService(): ValidationService = {

    val xmlParser = new XmlParser()

    val domDocument = xmlParser.parse(getClass.getResource("/xml/CTValid1.xml")).right.get
    val scalaNode = xmlParser.domToScala(domDocument).right.get

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
    when(processingEngine.fixAndValidateAsV2(anyObject(), anyString(), anyString(), anyString())).thenReturn(true)
    processingEngine
  }

  def aLegacyConnector(): LegacyConnector = {
    val connector = mock[LegacyConnector]
    when(connector.sendBAReport(any(classOf[BAReportRequest]))(any[ExecutionContext], any[HeaderCarrier]))
      .thenAnswer(new Answer[Future[Int]] {
        override def answer(invocation: InvocationOnMock): Future[Int] = Future.successful(200)
      })

    when(connector.validate(any(classOf[LoginDetails]))(any[ExecutionContext], any[HeaderCarrier]))
      .thenAnswer(new Answer[Future[Try[Int]]] {
        override def answer(invocation: InvocationOnMock): Future[Try[Int]] = Future.successful(Try(200))
      })

    connector
  }

  def aEmailConnector(): EmailConnector = {
    val emailConnector = mock[EmailConnector]
    when(emailConnector.sendEmail(anyString(), anyObject(), anyString, anyString, anyString, anyString, anyString, anyString)).thenAnswer(new Answer[Future[Unit]] {
      override def answer(invocationOnMock: InvocationOnMock): Future[Unit] = Future.successful({})
    })

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
