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

import javax.xml.transform.stream.StreamSource
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import org.scalatestplus.play.PlaySpec
import services.EbarsValidator
import uk.gov.hmrc.voabar.services.NdrValidationRules.{Rt01AndRt04AndRt03AndRt04MissingProposedEntryValidation, Rt05AndRt06AndRt07AndRt08AndRt9AndRt11MissingExistingEntryValidation}
import uk.gov.hmrc.voabar.models.{ReportErrorDetailCode => ErrorCode}

/**
 * Created by rgallet on 09/12/15.
 */
class RulesValidationEngineNdrSpec extends PlaySpec with GuiceOneAppPerSuite {

  "Rt01AndRt04AndRt03AndRt04MissingProposedEntryValidation" must {
    "report missing existing entry" in {
      val ebarsValidator = new EbarsValidator
      val reports        = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/RulesValidationEngine/ndr/NDR_EASTRIDING_RT1_NO_PROPERTIES.xml")))

      val result = Rt01AndRt04AndRt03AndRt04MissingProposedEntryValidation.apply(reports)

      result.get.errorCode mustBe (ErrorCode.Rt01AndRt04AndRt03AndRt04MissingProposedEntryValidation)
      // result.get.value must be("The proposed property address is missing from this report.") //no messages
    }
  }

  "Rt05AndRt06AndRt07AndRt08AndRt9AndRt11MissingExistingEntryValidation" must {
    "report missing existing entry" in {
      val ebarsValidator = new EbarsValidator
      val reports        = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/RulesValidationEngine/ndr/NDR_EASTRIDING_RT5_NO_PROPERTIES.xml")))

      val result = Rt05AndRt06AndRt07AndRt08AndRt9AndRt11MissingExistingEntryValidation.apply(reports)

      result.get.errorCode must be(ErrorCode.Rt05AndRt06AndRt07AndRt08AndRt9AndRt11MissingExistingEntryValidation)
      // result.get.value must be("The existing property address is missing from this report.") no messages
    }
  }
}
