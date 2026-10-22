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

package uk.gov.hmrc.vo.autobars.models.event

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.vo.autobars.models.{BarEbarError, BarEmailError, BarError, BarMongoError, BarSubmissionValidationError, BarUnknownError, BarValidationError, BarXmlError, BarXmlValidationError, Error, ReportError}
import uk.gov.hmrc.vo.autobars.util.BillingAuthorities
import uk.gov.hmrc.vo.service.model.event.AuditEvent

/**
  * @author Yuriy Tumakha
  */
case class ReportUploadFailed(
  username: String,
  baName: String,
  message: Option[String] = None,
  errors: Option[List[Error]] = None,
  reportErrors: Option[List[ReportError]] = None,
  mongoError: Option[String] = None,
  ebarError: Option[String] = None,
  detail: Option[String] = None
) extends AuditEvent

object ReportUploadFailed:
  implicit val format: OFormat[ReportUploadFailed] = Json.format

  def apply(username: String, barError: BarError): ReportUploadFailed =
    val event = ReportUploadFailed(username, BillingAuthorities.getByUsername(username))

    barError match
      case barXmlError: BarXmlError                                   => event.copy(message = Some(barXmlError.message))
      case barXmlValidationError: BarXmlValidationError               => event.copy(errors = Some(barXmlValidationError.errors))
      case barValidationError: BarValidationError                     => event.copy(errors = Some(barValidationError.errors))
      case barSubmissionValidationError: BarSubmissionValidationError => event.copy(reportErrors = Some(barSubmissionValidationError.reportErrors))
      case barMongoError: BarMongoError                               => event.copy(mongoError = Some(barMongoError.mongoError))
      case barEbarError: BarEbarError                                 => event.copy(ebarError = Some(barEbarError.ebarError))
      case barEmailError: BarEmailError                               => event.copy(ebarError = Some(barEmailError.ebarError))
      case barUnknownError: BarUnknownError                           => event.copy(detail = Some(barUnknownError.detail))
