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

import services.EbarsValidator
import uk.gov.hmrc.vo.autobars.models.ReportErrorDetailCode as ErrorCode
import uk.gov.hmrc.vo.autobars.services.CtValidationRules.*
import uk.gov.hmrc.vo.unit.test.BaseAppSpec

import javax.xml.transform.stream.StreamSource

/**
  * Created by rgallet on 09/12/15.
  */
class RulesValidationEngineCtSpec extends BaseAppSpec:

  "Cr01AndCr02MissingExistingEntryValidation" should {
    "report missing existing entry" in {
      val reports = EbarsValidator().fromJson(StreamSource(getClass.getResourceAsStream("/json/RulesCorrectionEngine/Cornwall_CTax_CR01_NoEntry.json")))

      val result = Cr01AndCr02MissingExistingEntryValidation.apply(reports)

      result.map(_.errorCode) shouldBe Some(ErrorCode.Cr01AndCr02MissingExistingEntryValidation)
    }
  }

  "Cr03AndCr04MissingProposedEntryValidation" should {
    "report missing existing entry" in {
      val reports = EbarsValidator().fromJson(StreamSource(getClass.getResourceAsStream("/json/RulesCorrectionEngine/Cornwall_CTax_CR03_NoEntry.json")))

      val result = Cr03AndCr04MissingProposedEntryValidation.apply(reports)

      result.map(_.errorCode) shouldBe Some(ErrorCode.Cr03AndCr04MissingProposedEntryValidation)
    }
  }

  "Cr05AndCr12MissingProposedEntryValidation" should {
    "report missing both entries" in {
      val reports = EbarsValidator().fromJson(StreamSource(getClass.getResourceAsStream("/json/RulesCorrectionEngine/Cornwall_CTax_CR05_NoEntry.json")))

      val result = Cr05AndCr12MissingProposedEntryValidation.apply(reports)

      result.map(_.errorCode) shouldBe Some(ErrorCode.Cr05AndCr12MissingProposedEntryValidation)
    }

    "report missing existing entry" in {
      val reports = EbarsValidator().fromJson(StreamSource(getClass.getResourceAsStream("/json/RulesCorrectionEngine/Cornwall_CTax_CR05_ProposedEntry.json")))

      val result = Cr05AndCr12MissingProposedEntryValidation.apply(reports)

      result.map(_.errorCode) shouldBe Some(ErrorCode.Cr05AndCr12MissingProposedEntryValidation)
    }
  }

  "Cr06AndCr07AndCr09AndCr10AndCr14MissingProposedEntryValidation" should {
    "report missing existing entry - CR06" in {
      val reports = EbarsValidator().fromXml(StreamSource(getClass.getResourceAsStream("/xml/RulesValidationEngine/CR06_NEITHEREXISTING_OR_PROPOSED.xml")))

      val result = Cr06AndCr07AndCr09AndCr10AndCr14MissingProposedEntryValidation.apply(reports)

      result.map(_.errorCode) shouldBe Some(ErrorCode.Cr06AndCr07AndCr09AndCr10AndCr14MissingProposedEntryValidation)
    }

    "report missing existing entry - CR14" in {
      val reports = EbarsValidator().fromXml(StreamSource(getClass.getResourceAsStream("/xml/RulesCorrectionEngine/CR14_PROPOSED_ENTRIES.xml")))

      val result = Cr06AndCr07AndCr09AndCr10AndCr14MissingProposedEntryValidation.apply(reports)

      result.map(_.errorCode) shouldBe Some(ErrorCode.Cr06AndCr07AndCr09AndCr10AndCr14MissingProposedEntryValidation)
    }
  }

  "Cr08InvalidCodeValidation" should {
    "report missing existing entry" in {
      val reports = EbarsValidator().fromJson(StreamSource(getClass.getResourceAsStream("/json/RulesCorrectionEngine/Cornwall_CTax_CR08_BothEntries.json")))

      val result = Cr08InvalidCodeValidation.apply(reports)

      result.map(_.errorCode) shouldBe Some(ErrorCode.Cr08InvalidCodeValidation)
    }
  }
