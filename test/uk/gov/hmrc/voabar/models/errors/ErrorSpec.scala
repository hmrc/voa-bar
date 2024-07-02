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

package uk.gov.hmrc.voabar.models.errors

import org.mongodb.scala.bson.BsonString
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.voabar.models.Error
import uk.gov.hmrc.voabar.util.ErrorCode
import uk.gov.hmrc.voabar.util.ErrorCode.*

class ErrorSpec extends PlaySpec {

  val code = CHARACTER
  val errorValue = Seq("testing error")

  val error = Error(code, errorValue)

  "Given an error code and an error value produce an Error model" in {
    error.code mustBe code
    error.values mustBe errorValue
  }

  "return same instance after deserialization" in {
    val res = ErrorCode.errorCodeReader(BsonString("1010"))

    res mustBe theSameInstanceAs(BA_CODE_MATCH)

    res.hashCode() mustBe BA_CODE_MATCH.hashCode()
  }

  "serialize ErrorCode to appropriate code" in {
    val res = ErrorCode.errorCodeWriter(UNSUPPORTED_TAX_TYPE)

    res mustBe BsonString("1020")
  }

  "return UNKNOWN_ERROR on deserialization for unknown code" in {
    val res = ErrorCode.errorCodeReader(BsonString("13777666"))

    res mustBe UNKNOWN_ERROR
  }

}
