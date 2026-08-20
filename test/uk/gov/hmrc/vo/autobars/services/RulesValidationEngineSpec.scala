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
import uk.gov.hmrc.vo.unit.test.BaseAppSpec

import javax.xml.transform.stream.StreamSource

/**
  * Created by rgallet on 09/12/15.
  */
class RulesValidationEngineSpec extends BaseAppSpec:

  "RulesValidationEngine" should {
    "not have postcode errors" in {
      val reports = EbarsValidator().fromXml(StreamSource(getClass.getResourceAsStream("/xml/RulesValidationEngine/ShouldNotHavePostcodeErrors.xml")))

      val result = RulesValidationEngine().applyRules(reports)

      result should have size 0
    }
  }

  "TextAddressPostcodeValidation" should {
    "valid postcode" in {
      val reports = EbarsValidator().fromJson(StreamSource(getClass.getResourceAsStream("/json/RulesCorrectionEngine/Cornwall_CTax_CR08_BothEntries.json")))

      val result = TextAddressPostcodeValidation.apply(reports)

      result shouldBe None
    }

    "have no error" in {
      val reports = EbarsValidator().fromXml(StreamSource(getClass.getResourceAsStream("/xml/RulesValidationEngine/SingleReport_Postcodeerror.xml")))

      val result = TextAddressPostcodeValidation.apply(reports)

      result shouldBe None
    }

    "invalid postcode" in {
      val reports = EbarsValidator().fromJson(StreamSource(getClass.getResourceAsStream("/json/RulesCorrectionEngine/Cornwall_CTax_CR08_InvalidPostcode.json")))

      val result = TextAddressPostcodeValidation.apply(reports)

      result.map(_.errorCode) shouldBe Some(ErrorCode.TextAddressPostcodeValidation)
    }
  }

  "OccupierContactAddressesPostcodeValidation" should {
    "valid postcode" in {
      val reports = EbarsValidator().fromJson(StreamSource(getClass.getResourceAsStream("/json/RulesCorrectionEngine/Cornwall_CTax_CR08_BothEntries.json")))

      val result = OccupierContactAddressesPostcodeValidation.apply(reports)

      result shouldBe None
    }

    "have no error" in {
      val reports = EbarsValidator().fromXml(StreamSource(getClass.getResourceAsStream("/xml/RulesValidationEngine/SingleReport_Postcodeerror.xml")))

      val result = OccupierContactAddressesPostcodeValidation.apply(reports)

      result shouldBe None
    }

    "invalid postcode" in {
      val reports = EbarsValidator().fromJson(StreamSource(getClass.getResourceAsStream("/json/RulesCorrectionEngine/Cornwall_CTax_CR08_InvalidPostcode.json")))

      val result = OccupierContactAddressesPostcodeValidation.apply(reports)

      result.map(_.errorCode) shouldBe Some(ErrorCode.OccupierContactAddressesPostcodeValidation)
    }
  }

  "RemarksValidation" should {
    "valid remarks" in {
      val reports = EbarsValidator().fromJson(StreamSource(getClass.getResourceAsStream("/json/RulesCorrectionEngine/Cornwall_CTax_CR08_BothEntries.json")))

      val result = RemarksValidation.apply(reports)

      result shouldBe None
    }

    "invalid remarks - too long" in {
      val reports =
        EbarsValidator().fromJson(StreamSource(getClass.getResourceAsStream("/json/RulesValidationEngine/Cornwall_CTax_InvalidStreetDescription1.json")))

      val result = RemarksValidation.apply(reports)

      result.map(_.errorCode) shouldBe Some(ErrorCode.RemarksValidationTooLong)
    }

    "invalid remarks - too short" in {
      val reports =
        EbarsValidator().fromJson(StreamSource(getClass.getResourceAsStream("/json/RulesValidationEngine/Cornwall_CTax_InvalidStreetDescription2.json")))

      val result = RemarksValidation.apply(reports)

      result.map(_.errorCode) shouldBe Some(ErrorCode.RemarksValidationNotEmpty)
    }
  }

  "PropertyPlanReferenceNumberValidation" should {
    "valid remarks" in {
      val reports = EbarsValidator().fromJson(StreamSource(getClass.getResourceAsStream("/json/RulesCorrectionEngine/Cornwall_CTax_CR08_BothEntries.json")))

      val result = PropertyPlanReferenceNumberValidation.apply(reports)

      result shouldBe None
    }

    "invalid PropertyPlanReferenceNumber - too long" in {
      val reports =
        EbarsValidator().fromJson(StreamSource(getClass.getResourceAsStream("/json/RulesValidationEngine/Cornwall_CTax_InvalidStreetDescription1.json")))

      val result = PropertyPlanReferenceNumberValidation.apply(reports)

      result.map(_.errorCode) shouldBe Some(ErrorCode.PropertyPlanReferenceNumberValidation)
    }
  }
