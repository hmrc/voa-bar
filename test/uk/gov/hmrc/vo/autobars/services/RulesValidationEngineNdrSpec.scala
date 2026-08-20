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
import uk.gov.hmrc.vo.autobars.services.NdrValidationRules.{Rt01AndRt04AndRt03AndRt04MissingProposedEntryValidation, Rt05AndRt06AndRt07AndRt08AndRt9AndRt11MissingExistingEntryValidation}
import uk.gov.hmrc.vo.unit.test.BaseAppSpec

import javax.xml.transform.stream.StreamSource

/**
  * Created by rgallet on 09/12/15.
  */
class RulesValidationEngineNdrSpec extends BaseAppSpec:

  "Rt01AndRt04AndRt03AndRt04MissingProposedEntryValidation" should {
    "report missing existing entry" in {
      val reports = EbarsValidator().fromXml(StreamSource(getClass.getResourceAsStream("/xml/RulesValidationEngine/ndr/NDR_EASTRIDING_RT1_NO_PROPERTIES.xml")))

      val result = Rt01AndRt04AndRt03AndRt04MissingProposedEntryValidation.apply(reports)

      result.map(_.errorCode) shouldBe Some(ErrorCode.Rt01AndRt04AndRt03AndRt04MissingProposedEntryValidation)
    }
  }

  "Rt05AndRt06AndRt07AndRt08AndRt9AndRt11MissingExistingEntryValidation" should {
    "report missing existing entry" in {
      val reports = EbarsValidator().fromXml(StreamSource(getClass.getResourceAsStream("/xml/RulesValidationEngine/ndr/NDR_EASTRIDING_RT5_NO_PROPERTIES.xml")))

      val result = Rt05AndRt06AndRt07AndRt08AndRt9AndRt11MissingExistingEntryValidation.apply(reports)

      result.map(_.errorCode) shouldBe Some(ErrorCode.Rt05AndRt06AndRt07AndRt08AndRt9AndRt11MissingExistingEntryValidation)
    }
  }
