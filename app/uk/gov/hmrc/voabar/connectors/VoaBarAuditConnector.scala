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

package uk.gov.hmrc.voabar.connectors

import play.api.libs.json.{JsString, Json, OWrites}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.voabar.util.BillingAuthorities

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class VoaBarAuditConnector @Inject() (audit: AuditConnector)(implicit val ec: ExecutionContext) {

  def userLogin(username: String)(implicit hc: HeaderCarrier): Unit =
    audit.sendExplicitAudit("user-login", Map("username" -> username, "baName" -> BillingAuthorities.find(username).getOrElse("NONE")))

  def sendReport(
    username: String,
    uuid: String,
    billingAuthority: String,
    transactionId: String,
    reportNumber: String,
    xml: String,
    fileName: String
  )(implicit hc: HeaderCarrier
  ): Unit = {

    val baName = BillingAuthorities.find(username).getOrElse("NONE")

    val detail = Json.obj(
      "username"         -> username,
      "baName"           -> baName,
      "autobarsJobId"    -> uuid,
      "billingAuthority" -> billingAuthority,
      "transactionId"    -> transactionId,
      "reportNumber"     -> reportNumber,
      "fileName"         -> fileName,
      "xml"              -> xml
    )

    audit.sendExplicitAudit("OutboundCall", detail)
  }

  def successfulReportUploaded(username: String, reports: Int)(implicit ec: ExecutionContext, hc: HeaderCarrier): Unit =
    audit.sendExplicitAudit(
      "report-upload",
      Map("username" -> username, "baName" -> BillingAuthorities.find(username).getOrElse("NONE"), "reports" -> reports.toString)
    )

  def reportUploadFailed[T](username: String, error: T)(implicit ec: ExecutionContext, hc: HeaderCarrier, w: OWrites[T]) = {
    val oErrors = w.writes(error)

    audit.sendExplicitAudit(
      "report-upload",
      oErrors +
        ("username" -> JsString(username)) +
        ("baName"   -> JsString(BillingAuthorities.find(username).getOrElse("NONE")))
    )
  }

}
