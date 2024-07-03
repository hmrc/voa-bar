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

import java.time.LocalDate
import play.api.libs.json.{Format, Json, OFormat}

sealed trait CrSubmission {
  def baReport: String
  def baRef: String
  def effectiveDate: LocalDate
}

object Address { implicit val format: OFormat[Address] = Json.format[Address] }
case class Address(line1: String, line2: String, line3: Option[String], line4: Option[String], postcode: String)
object ContactDetails { implicit val format: OFormat[ContactDetails] = Json.format[ContactDetails] }
case class ContactDetails(firstName: String, lastName: String, email: Option[String], phoneNumber: Option[String])
object Cr01Cr03Submission { val format: OFormat[Cr01Cr03Submission] = Json.format[Cr01Cr03Submission] }

case class Cr01Cr03Submission(
  reasonReport: Option[ReasonReportType],
  removalReason: Option[RemovalReasonType],
  otherReason: Option[String],
  baReport: String,
  baRef: String,
  uprn: Option[String],
  address: Address,
  propertyContactDetails: ContactDetails,
  sameContactAddress: Boolean,
  contactAddress: Option[Address],
  effectiveDate: LocalDate,
  havePlaningReference: Boolean,
  planningRef: Option[String],
  noPlanningReference: Option[NoPlanningReferenceType],
  comments: Option[String]
) extends CrSubmission

case class Cr05AddProperty(
  uprn: Option[String],
  address: Address,
  propertyContactDetails: ContactDetails,
  sameContactAddress: Boolean,
  contactAddress: Option[Address]
)

object Cr05AddProperty { implicit val format: OFormat[Cr05AddProperty] = Json.format[Cr05AddProperty] }

case class Cr05Submission(
  baReport: String,
  baRef: String,
  effectiveDate: LocalDate,
  proposedProperties: Seq[Cr05AddProperty],
  existingPropertis: Seq[Cr05AddProperty],
  planningRef: Option[String],
  noPlanningReference: Option[NoPlanningReferenceType],
  comments: Option[String]
) extends CrSubmission

object Cr05Submission {

  val REPORT_SUBMISSION_KEY = "Cr05Submission"

  implicit val format: Format[Cr05Submission] = Json.format[Cr05Submission]
}
