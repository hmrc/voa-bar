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

package uk.gov.hmrc.voabar.services

import java.io.Reader

import org.scalatestplus.play.PlaySpec

class InputSpec extends PlaySpec {
  val input      = new Input
  val encoding   = "some encoding"
  val stringData = "some data"

  class FakeReader extends Reader {
    override def read(cbuf: Array[Char], off: Int, len: Int): Int = 1

    override def close(): Unit = ()
  }

  val reader = new FakeReader

  "An input class " must {

    "setCertifiedText method should set the certifiedText variable to true given a true parameter" in {
      input.setCertifiedText(true)
      input.getCertifiedText mustBe true
    }

    "setCertifiedText method should set the certifiedText variable to false given a false parameter" in {
      input.setCertifiedText(false)
      input.getCertifiedText mustBe false
    }

    "setEncoding method should set the encoding variable to 'some encoding' value" in {
      input.setEncoding(encoding)
      input.getEncoding mustBe encoding
    }

    "getCertifiedText method should return false when certifiedText hasn't been set up" in {
      input.getCertifiedText mustBe false
    }

    "setStringData method shouls set the stringData variable to 'some data' when calling the method with 'some data' value" in {
      input.setStringData(stringData)
      input.getStringData mustBe stringData
    }

    "setCharacterStream method should set the variable reader to the given value" in {
      input.setCharacterStream(reader)
      input.getCharacterStream.hashCode mustBe reader.hashCode
    }
  }

}
