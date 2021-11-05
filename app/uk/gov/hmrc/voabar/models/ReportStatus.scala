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

package uk.gov.hmrc.voabar.models

import java.time.ZonedDateTime

import play.api.libs.json._
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

sealed trait ReportStatusType {
  val value: String = {
    val a: Class[_ <: ReportStatusType] = getClass.asSubclass(getClass)
    val u: String = a.getSimpleName.replace("$", "")
    u
  }
}

case object Pending extends ReportStatusType
case object Verified extends ReportStatusType
case object Failed extends ReportStatusType
case object Unknown extends ReportStatusType
case object Submitted extends ReportStatusType
case object Cancelled extends ReportStatusType
case object Done extends ReportStatusType


final case class ReportStatus(
                               id: String,
                               created: ZonedDateTime,
                               url: Option[String] = None,
                               checksum: Option[String] = None,          //TODO -  Do we nee this?
                               errors: Option[Seq[Error]] = Some(Seq()), //TODO - doesn't need to be Option, Seq is also Option
                               reportErrors: Seq[ReportError] = Seq(),
                               baCode: Option[String] = None,            //TODO - Make mandatory. Submission without BA can't exist.
                               status: Option[String] = Some(Pending.value), //TODO - Make this mandatory and for all new put default values in deserialisation
                               filename: Option[String] = None,
                               totalReports: Option[Int] = None,
                               report: Option[JsObject] = None
                             ) {

  def redacted: ReportStatus = {
    this.copy(url = this.url.map(_ => "***redacted***"))
  }

}

object ReportStatus {
  import ReactiveMongoFormats.mongoEntity

  implicit val format =  mongoEntity {
    Json.using[Json.WithDefaultValues].format[ReportStatus]
  }
}
