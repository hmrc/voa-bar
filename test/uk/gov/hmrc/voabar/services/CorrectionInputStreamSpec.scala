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

package uk.gov.hmrc.voabar.services

import java.io.ByteArrayInputStream

import org.apache.commons.io.IOUtils
import org.scalatestplus.play.PlaySpec

class CorrectionInputStreamSpec extends PlaySpec {

  "CorrectionInputStream" should {
    "replace nbsp entity in byte stream" in {
      val input = "This is&nbsp;text".getBytes("UTF-8")
      val in = CorrectionInputStream(new ByteArrayInputStream(input))

      val result = IOUtils.toString(in, "UTF-8")

      result mustBe("This is text")

    }
  }


}
