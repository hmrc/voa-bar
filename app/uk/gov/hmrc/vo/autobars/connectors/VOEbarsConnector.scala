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

package uk.gov.hmrc.vo.autobars.connectors

import com.google.inject.ImplementedBy
import ebars.xml.BAreports
import play.api.Logging
import play.api.http.Status.{INTERNAL_SERVER_ERROR, SERVICE_UNAVAILABLE}
import play.mvc.Http.Status.OK
import services.EbarsValidator
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.vo.autobars.models.EbarsRequests.*
import uk.gov.hmrc.vo.autobars.models.LoginDetails
import uk.gov.hmrc.vo.autobars.services.{EbarsApiError, EbarsClientV2}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
  * @author Yuriy Tumakha
  */
@Singleton
class DefaultVOEbarsConnector @Inject() (
  ebarsClientV2: EbarsClientV2,
  audit: VOBarAuditConnector
) extends VOEbarsConnector
  with Logging:

  val ebarsValidator: EbarsValidator = EbarsValidator()

  override def validate(loginDetails: LoginDetails): Future[Try[Int]] =
    ebarsClientV2.login(loginDetails.username, loginDetails.password)

  def sendBAReport(baReportRequest: BAReportRequest)(using ec: ExecutionContext, headerCarrier: HeaderCarrier): Future[Int] =
    Try(ebarsValidator.fromJson(baReportRequest.propertyReport)) map { reports =>
      val xml = ebarsValidator.toXml(reports)
      (reports, xml, ebarsValidator.validate(xml))
    } match
      case Success((reports, _, errors)) if errors.hasErrors =>
        import models.EbarsBAreports.*
        Future.failed(RuntimeException(s"propertyReferenceNumbers: ${reports.uniquePropertyReferenceNumbers}. errors: $errors"))
      case Success((reports, xml, _))                        =>
        sendXML(baReportRequest, reports, xml)
      case Failure(e) if e.getCause != null                  => Future.failed(e.getCause)
      case Failure(e)                                        => Future.failed(e)

  private def sendXML(baReportRequest: BAReportRequest, reports: BAreports, xml: String)(using ec: ExecutionContext, headerCarrier: HeaderCarrier)
    : Future[Int] =
    ebarsClientV2.uploadXML(baReportRequest.username, baReportRequest.password, xml, baReportRequest.attempt).flatMap {
      case Success(_)                =>
        val billingAuthority     = reports.getBAreportHeader.getBillingAuthority
        val billingAuthorityCode = reports.getBAreportHeader.getBillingAuthorityIdentityCode
        val reportContent        = reports.getBApropertyReport.get(0).getContent
        val transactionId        = reportContent.get(1).getValue.toString
        val reportNumber         = reportContent.get(3).getValue.toString

        logger.info(s"Report [$reportNumber]/${baReportRequest.attempt} for $billingAuthority [$billingAuthorityCode]: TxId:[$transactionId] to eBars")

        val fileName = s"$billingAuthority-$transactionId-$reportNumber.xml"

        audit.sendReport(baReportRequest.username, baReportRequest.uuid, billingAuthority, transactionId, reportNumber, xml, fileName)

        Future.successful(OK)
      case Failure(e: EbarsApiError) =>
        e.status match
          case OK                    => Future.failed(RuntimeException(s"eBars response status: ${e.status}. ${e.getMessage}"))
          case SERVICE_UNAVAILABLE   => Future.failed(RuntimeException("eBars UNAVAILABLE"))
          case INTERNAL_SERVER_ERROR => Future.failed(RuntimeException("eBars INTERNAL_SERVER_ERROR"))
          case status                => Future.failed(RuntimeException(s"Unspecified eBars error, status: $status"))
      case Failure(e)                =>
        logger.warn(s"Couldn't send BA Reports. ${e.getMessage}", e)
        Future.failed(e)
    }

@ImplementedBy(classOf[DefaultVOEbarsConnector])
trait VOEbarsConnector:
  def validate(loginDetails: LoginDetails): Future[Try[Int]]

  def sendBAReport(baReport: BAReportRequest)(using ec: ExecutionContext, headerCarrier: HeaderCarrier): Future[Int]
