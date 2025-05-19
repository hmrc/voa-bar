/*
 * Copyright 2025 HM Revenue & Customs
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

import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpec

import javax.xml.transform.stream.StreamSource
import org.scalatest.OptionValues
import services.EbarsValidator

/**
  * Created by rgallet on 09/12/15.
  */
class EbarsXmlCutterNDRSpec extends AnyWordSpec with should.Matchers with OptionValues {
  val ebarsValidator = new EbarsValidator

  "extracting CR code" should {
    "return 11" in {
      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/RulesCorrectionEngine/NDR_EASTRIDING_BOTH_PROPERTIES.xml")))

      EbarsXmlCutter.extractCR(reports) should contain("11")
    }

    "return None if missing" in {
      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/RulesCorrectionEngine/NDR_EASTRIDING_MISSING_CR_CODE.xml")))
      EbarsXmlCutter.extractCR(reports) should be(None)
    }

    "return None if invalid" in {
      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/RulesCorrectionEngine/NDR_EASTRIDING_INVALID_CR_CODE.xml")))
      EbarsXmlCutter.extractCR(reports) should be(None)
    }
  }

  "working from a file with just a proposed entry" should {
    "move first existing entry to proposed" in {
      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/RulesCorrectionEngine/NDR_EASTRIDING_EXISTING_PROPERTIES.xml")))

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size 0
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size 1

      EbarsXmlCutter.convertExistingEntriesIntoProposedEntries(reports)

      EbarsXmlCutter.findLastTypeOfTaxIdx(reports)   should contain(4)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size 0
      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size 1
    }
  }
}
