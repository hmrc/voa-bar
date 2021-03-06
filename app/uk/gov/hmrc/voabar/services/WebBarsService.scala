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

import akka.actor.ActorSystem
import com.google.inject.ImplementedBy
import ebars.xml.BAreports
import javax.inject.{Inject, Singleton}
import javax.xml.bind.{JAXBContext, JAXBException}
import play.api.Logger
import play.api.libs.json.JsString
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.voabar.models.{Cr01Cr03Submission, LoginDetails, ReportStatus}
import uk.gov.hmrc.voabar.repositories.SubmissionStatusRepository
import uk.gov.hmrc.voabar.util.{BillingAuthorities, Cr01Cr03SubmissionXmlGenerator}

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[DefaultWebBarsService])
trait WebBarsService {
  def newSubmission(reportStatus: ReportStatus, username: String, password: String): Unit
}

@Singleton
class DefaultWebBarsService @Inject() (actorSystem: ActorSystem,submissionRepository: SubmissionStatusRepository, reportUploadService: ReportUploadService)(
  implicit ec: ExecutionContext) extends WebBarsService {

  val log = Logger(this.getClass)

  def newSubmission(reportStatus: ReportStatus, username: String, password: String): Unit = {
    if(reportStatus.report.isDefined) {
      processReport(reportStatus, username, password)
    }
  }



  def processReport(reportStatus: ReportStatus, username: String, password: String): Unit = Future {
    val cr01cr03Submission = DefaultWebBarsService.readReport(reportStatus)

    cr01cr03Submission.foreach { submission =>
      implicit val hc = HeaderCarrier()
      val cr01cr03SubmissionXmlGenerator = new Cr01Cr03SubmissionXmlGenerator(submission, username.substring(2).toInt,
        BillingAuthorities.find(username).getOrElse("Unknown"), reportStatus.id)

      val areports = cr01cr03SubmissionXmlGenerator.generateXml()
      log.debug("Generated report")
      logReports(areports)
      reportUploadService.upload(LoginDetails(username, password), areports, reportStatus.id)
    }
  }.recover {
    case x: Exception => {
      log.warn(s"Unable to process webBars report : ${reportStatus.redacted}")
    }
  }

  import javax.xml.bind.Marshaller
  import java.io.StringWriter

  // Temporary methods to help validate the ticket generation
  private def logReports(employee: BAreports): Unit = {
    try {
      val jaxbContext = JAXBContext.newInstance(classOf[BAreports])
      val jaxbMarshaller = jaxbContext.createMarshaller
      jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, java.lang.Boolean.TRUE)
      val sw = new StringWriter
      jaxbMarshaller.marshal(employee, sw)
      val xmlContent = sw.toString
      log.debug(xmlContent)
    } catch {
      case e: JAXBException =>
       log.warn(e.getMessage, e)
    }
  }

}

object DefaultWebBarsService {

  def readReport(reportStatus: ReportStatus): Option[Cr01Cr03Submission] = {
    reportStatus.report
      .map(_.value)
      .filter(x => x.get("type").exists {
        case JsString(typeValue) => typeValue == "Cr03Submission" || typeValue == "Cr01Cr03Submission"
        case _ => false
      })
      .flatMap(x => x.get("submission")).flatMap(x => Cr01Cr03Submission.format.reads(x).asOpt)
  }
}
