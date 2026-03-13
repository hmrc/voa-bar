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

import com.google.inject.ImplementedBy
import ebars.xml.BAreports
import jakarta.xml.bind.{JAXBContext, JAXBException, Marshaller}
import play.api.Logging
import play.api.libs.json.JsString
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.vo.autobars.models.*
import uk.gov.hmrc.vo.autobars.util.{BillingAuthorities, XmlSubmissionGenerator}

import java.io.StringWriter
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[DefaultWebBarsService])
trait WebBarsService:
  def newSubmission(reportStatus: ReportStatus, username: String, password: String): Unit

@Singleton
class DefaultWebBarsService @Inject() (
  reportUploadService: ReportUploadService
)(using ec: ExecutionContext
) extends WebBarsService
  with Logging:

  def newSubmission(reportStatus: ReportStatus, username: String, password: String): Unit =
    if reportStatus.report.isDefined then
      processReport(reportStatus, username, password)

  private def processReport(reportStatus: ReportStatus, username: String, password: String): Unit = Future {
    val submission = DefaultWebBarsService.readReport(reportStatus)

    submission.foreach { submission =>
      given HeaderCarrier = HeaderCarrier()

      val submissionGenerator =
        XmlSubmissionGenerator(submission, username.substring(2).toInt, BillingAuthorities.find(username).getOrElse("Unknown"), reportStatus.id)

      val reports = submissionGenerator.generateXml()
      logger.debug("Generated report")
      logReports(reports)
      reportUploadService.upload(LoginDetails(username, password), reports, reportStatus.id)
    }
  }.recover {
    case ex: Exception =>
      logger.warn(s"Unable to process webBars report : ${reportStatus.redacted}", ex)
  }

  // Temporary methods to help validate the ticket generation
  private def logReports(employee: BAreports): Unit =
    try
      val jaxbContext    = JAXBContext.newInstance(classOf[BAreports])
      val jaxbMarshaller = jaxbContext.createMarshaller
      jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, java.lang.Boolean.TRUE)
      val sw             = StringWriter()
      jaxbMarshaller.marshal(employee, sw)
      val xmlContent     = sw.toString
      logger.debug(xmlContent)
    catch
      case e: JAXBException =>
        logger.warn(e.getMessage, e)

object DefaultWebBarsService:

  def readReport(reportStatus: ReportStatus): Option[CrSubmission] =
    reportStatus.report
      .map(_.value)
      .filter(x => x.contains("type") && x.contains("submission"))
      .flatMap { x =>
        x("type") match
          case JsString("Cr03Submission")                     => Cr01Cr03Submission.format.reads(x("submission")).asOpt
          case JsString("Cr01Cr03Submission")                 => Cr01Cr03Submission.format.reads(x("submission")).asOpt
          case JsString(Cr05Submission.REPORT_SUBMISSION_KEY) => Cr05Submission.format.reads(x("submission")).asOpt
          case _                                              => None
      }
