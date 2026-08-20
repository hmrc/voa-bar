/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.vo.autobars.services

import java.io.Reader
import uk.gov.hmrc.vo.unit.test.BaseSpec

class InputSpec extends BaseSpec:

  private val input      = Input()
  private val encoding   = "some encoding"
  private val stringData = "some data"
  private val reader     = FakeReader()

  class FakeReader extends Reader:
    override def read(cbuf: Array[Char], off: Int, len: Int): Int = 1

    override def close(): Unit = ()

  "An input class " should {
    "setCertifiedText method should set the certifiedText variable to true given a true parameter" in {
      input.setCertifiedText(true)
      input.getCertifiedText shouldBe true
    }

    "setCertifiedText method should set the certifiedText variable to false given a false parameter" in {
      input.setCertifiedText(false)
      input.getCertifiedText shouldBe false
    }

    "setEncoding method should set the encoding variable to 'some encoding' value" in {
      input.setEncoding(encoding)
      input.getEncoding shouldBe encoding
    }

    "getCertifiedText method should return false when certifiedText hasn't been set up" in {
      input.getCertifiedText shouldBe false
    }

    "setStringData method should set the stringData variable to 'some data' when calling the method with 'some data' value" in {
      input.setStringData(stringData)
      input.getStringData shouldBe stringData
    }

    "setCharacterStream method should set the variable reader to the given value" in {
      input.setCharacterStream(reader)
      input.getCharacterStream.hashCode shouldBe reader.hashCode
    }
  }
