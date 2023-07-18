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

package uk.gov.hmrc.voabar.models

import play.api.libs.json.{Format, Json, OFormat}
import uk.gov.hmrc.voabar.util.JavaEnumUtils

case class ReportErrorDetail(errorCode: ReportErrorDetailCode, values: Seq[String] = Seq.empty[String])

object ReportErrorDetail {
  implicit val errorCodeFormat: Format[ReportErrorDetailCode] = JavaEnumUtils.format[ReportErrorDetailCode]
  implicit val format: OFormat[ReportErrorDetail] = Json.format[ReportErrorDetail]

}



case class ReportError(reportNumber: Option[String],
                       baTransaction: Option[String],
                       uprn: Seq[Long],
                       errors: Seq[ReportErrorDetail]
                      )

object ReportError {
  implicit val format: OFormat[ReportError] = Json.format[ReportError]
}
