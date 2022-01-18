/*
 * Copyright 2022 HM Revenue & Customs
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

import play.api.libs.json.{Format, JsError, JsResult, JsString, JsSuccess, JsValue}

sealed trait RemovalReasonType {
  def xmlValue: String
}

case object Demolition extends RemovalReasonType {
  override def xmlValue: String = "Property demolished."
}
case object Disrepair extends RemovalReasonType {
  override def xmlValue: String = "Property in disrepair."
}

case object Derelict extends RemovalReasonType {
  override def xmlValue: String = "Property derelict."
}

case object Renovating extends RemovalReasonType {
  override def xmlValue: String = "It is being renovated"
}

// TODO Remove
case object NotComplete extends RemovalReasonType {
  override def xmlValue: String = "Banded too soon or not complete."
}

// TODO remove
case object BandedTooSoon extends RemovalReasonType {
  override def xmlValue: String = "Banded too soon or not complete."
}

case object BandedTooSoonOrNotComplete extends RemovalReasonType {
  override def xmlValue: String = "Banded too soon or not complete."
}

case object CaravanRemoved extends RemovalReasonType {
  override def xmlValue: String = "Caravan not sole main, removed."
}

case object Duplicate extends RemovalReasonType {
  override def xmlValue: String = "Duplicate property."
}
case object OtherReason extends RemovalReasonType {
  override def xmlValue: String = "Other reason"
}



object RemovalReasonType {
  implicit val format: Format[RemovalReasonType] = new Format[RemovalReasonType] {
    override def reads(json: JsValue): JsResult[RemovalReasonType] = {
      json match  {
        case JsString("Demolition") => JsSuccess(Demolition)
        case JsString("Disrepair") => JsSuccess(Disrepair)
        case JsString("Derelict") => JsSuccess(Derelict)
        case JsString("Renovating") => JsSuccess(Renovating)
        case JsString("NotComplete") => JsSuccess(NotComplete)
        case JsString("BandedTooSoon") => JsSuccess(BandedTooSoon)
        case JsString("BandedTooSoonOrNotComplete") => JsSuccess(BandedTooSoonOrNotComplete)
        case JsString("CaravanRemoved") => JsSuccess(CaravanRemoved)
        case JsString("Duplicate") => JsSuccess(Duplicate)
        case JsString("OtherReason") => JsSuccess(OtherReason)
        case x => JsError(s"Unable to deserialize RemovalReasonType ${x}")
      }
    }

    override def writes(rrt: RemovalReasonType): JsValue = {
      rrt match  {
        case Demolition     => JsString("Demolition")
        case Disrepair      => JsString("Disrepair")
        case Derelict       => JsString("Derelict")
        case Renovating     => JsString("Renovating")
        case NotComplete    => JsString("NotComplete")
        case BandedTooSoon  => JsString("BandedTooSoon")
        case BandedTooSoonOrNotComplete  => JsString("BandedTooSoonOrNotComplete")
        case CaravanRemoved => JsString("CaravanRemoved")
        case Duplicate      => JsString("Duplicate")
        case OtherReason    => JsString("OtherReason")
      }
    }
  }
}
