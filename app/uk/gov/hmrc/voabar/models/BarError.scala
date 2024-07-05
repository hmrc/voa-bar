/*
 * Copyright 2024 HM Revenue & Customs
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

import play.api.libs.json.{JsObject, Json, OWrites}

sealed trait BarError

final case class BarXmlError(message: String) extends BarError

final case class BarXmlValidationError(errors: List[Error]) extends BarError

final case class BarValidationError(errors: List[Error]) extends BarError

final case class BarSubmissionValidationError(errors: List[ReportError]) extends BarError

final case class BarMongoError(error: String) extends BarError

final case class BarEbarError(ebarError: String) extends BarError

final case class BarEmailError(ebarError: String) extends BarError

final case class UnknownError(detail: String) extends BarError

object BarError {

  implicit val barXmlErrorFormat: OWrites[BarXmlError]                                   = Json.writes[BarXmlError]
  implicit val barXmlValidationErrorFormat: OWrites[BarXmlValidationError]               = Json.writes[BarXmlValidationError]
  implicit val barValidationErrorFormat: OWrites[BarValidationError]                     = Json.writes[BarValidationError]
  implicit val barSubmissionValidationErrorFormat: OWrites[BarSubmissionValidationError] = Json.writes[BarSubmissionValidationError]

  implicit val barMongoErrorFormat: OWrites[BarMongoError] = new OWrites[BarMongoError] {
    override def writes(o: BarMongoError): JsObject = Json.obj("mongoError" -> o.error)
  }
  implicit val barEbarErrorFormat: OWrites[BarEbarError]   = Json.writes[BarEbarError]
  implicit val barEmailErrorFormat: OWrites[BarEmailError] = Json.writes[BarEmailError]
  implicit val unknownErrorFormat: OWrites[UnknownError]   = Json.writes[UnknownError]

  implicit val format: OWrites[BarError] = Json.writes[BarError]
}
