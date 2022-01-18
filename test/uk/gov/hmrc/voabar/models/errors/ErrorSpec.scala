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

package uk.gov.hmrc.voabar.models.errors

import org.scalatestplus.play.PlaySpec
import reactivemongo.bson.BSONString
import uk.gov.hmrc.voabar.models.Error
import uk.gov.hmrc.voabar.util.{BA_CODE_MATCH, CHARACTER, ErrorCode}

class ErrorSpec extends PlaySpec {

  val code = CHARACTER
  val errorValue = Seq("testing error")

  val error = Error(code, errorValue)

  "Given an error code and an error value produce an Error model" in {
    error.code mustBe code
    error.values mustBe errorValue
  }

  "return same instance after deserialisation" in {

    val res = ErrorCode.errorCodeReader.read(BSONString("1010"))

    res.hashCode() mustBe BA_CODE_MATCH.hashCode()

    res mustBe theSameInstanceAs( BA_CODE_MATCH)

  }

}
