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

import ebars.xml.CtaxReasonForReportCodeContentType
import play.api.libs.json.{Format, JsError, JsResult, JsString, JsSuccess, JsValue}

sealed trait ReasonReportType {
  def xmlValue: CtaxReasonForReportCodeContentType
  def reasonForCodeDescription: String
}

case object AddProperty extends ReasonReportType {
  override def xmlValue: CtaxReasonForReportCodeContentType =
    CtaxReasonForReportCodeContentType.CR_03
  def reasonForCodeDescription: String = "New"
}
case object RemoveProperty extends ReasonReportType {
  override def xmlValue: CtaxReasonForReportCodeContentType =
    CtaxReasonForReportCodeContentType.CR_01
  def reasonForCodeDescription: String = "Demolished"
}


object ReasonReportType {
  implicit val format: Format[ReasonReportType] = new Format[ReasonReportType] {
    override def reads(json: JsValue): JsResult[ReasonReportType] = {
      json match  {
        case JsString("AddProperty") => JsSuccess(AddProperty)
        case JsString("RemoveProperty") => JsSuccess(RemoveProperty)
        case x => JsError(s"Unable to deserialize ReasonReportType ${x}")
      }
    }

    override def writes(o: ReasonReportType): JsValue = {
      o match  {
        case AddProperty       => JsString("AddProperty")
        case RemoveProperty => JsString("RemoveProperty")
      }
    }
  }
}
