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

import java.time.Instant
import play.api.libs.json._
import uk.gov.hmrc.mongo.play.json.formats.MongoFormats.mongoEntity

import scala.annotation.nowarn

sealed trait ReportStatusType {

  val value: String = {
    val a: Class[_ <: ReportStatusType] = getClass.asSubclass(getClass)
    val u: String                       = a.getSimpleName.replace("$", "")
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
  createdAt: Instant = Instant.now,
  url: Option[String] = None,
  checksum: Option[String] = None,
  errors: Seq[Error] = Seq(),
  reportErrors: Seq[ReportError] = Seq(),
  baCode: String,
  status: Option[String] = Some(Pending.value), // TODO status: String = Pending.value after release VOA-3532
  filename: Option[String] = None,
  totalReports: Option[Int] = None,
  report: Option[JsObject] = None
) {

  def redacted: ReportStatus =
    this.copy(url = this.url.map(_ => "***redacted***"))

}

object ReportStatus {

  import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats.Implicits._

  @nowarn
  implicit val format: Format[ReportStatus] = mongoEntity {
    Json.using[Json.WithDefaultValues].format[ReportStatus]
  }

}
