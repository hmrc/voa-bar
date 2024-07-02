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

package uk.gov.hmrc.voabar.util

import org.bson.BsonValue
import play.api.libs.json.*
import uk.gov.hmrc.mongo.play.json.Codecs

enum ErrorCode(val errorCode: String):
  case CHARACTER extends ErrorCode("1000")
  case ONE_PROPOSED extends ErrorCode("1001")
  case NONE_EXISTING extends ErrorCode("1002")
  case EITHER_ONE_EXISTING_OR_ONE_PROPOSED extends ErrorCode("1003")
  case ATLEAST_ONE_PROPOSED extends ErrorCode("1004")
  case ATLEAST_ONE_EXISTING extends ErrorCode("1005")
  case NOT_IN_USE extends ErrorCode("1006")
  case ONE_EXISTING extends ErrorCode("1007")
  case NONE_PROPOSED extends ErrorCode("1008")
  case BA_CODE_MATCH extends ErrorCode("1010")
  case BA_CODE_REPORT extends ErrorCode("1012")
  case UNSUPPORTED_TAX_TYPE extends ErrorCode("1020")
  case UNKNOWN_TYPE_OF_TAX extends ErrorCode("1021")
  case UNKNOWN_DATA_OBJECT extends ErrorCode("1022")
  case INVALID_XML_XSD extends ErrorCode("2000")
  case INVALID_XML extends ErrorCode("2001")
  case EBARS_UNAVAILABLE extends ErrorCode("3000")
  case UPSCAN_ERROR extends ErrorCode("4000")
  case TIMEOUT_ERROR extends ErrorCode("4500")
  case UNKNOWN_ERROR extends ErrorCode("5000")
end ErrorCode

object ErrorCode {

  private val errorCodeMap: Map[String, ErrorCode] =
    ErrorCode.values.map(e => e.errorCode -> e).toMap

  implicit val reader: Reads[ErrorCode] = (json: JsValue) => {
    val value = json.validate[String].get
    JsSuccess(errorCodeMap.getOrElse(value, UNKNOWN_ERROR))
  }

  implicit val writer: Writes[ErrorCode] =
    (o: ErrorCode) => Json.toJson[String](o.errorCode)

  implicit val errorCodeReader: BsonValue => ErrorCode = Codecs.fromBson(_)

  implicit val errorCodeWriter: ErrorCode => BsonValue = Codecs.toBson(_)

  //  implicit val errorCodeReader = new BSONReader[BSONString, ErrorCode] {
  //    override def read(bson: BSONString): ErrorCode = errorCodeMap.get(bson.value).get
  //  }
  //
  //  implicit val erorrCodeWriter = BSONWriter[ErrorCode, BSONString](e => BSONString(e.errorCode))
}
