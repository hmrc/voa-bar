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
import org.scalatest.OptionValues
import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpec
import services.EbarsValidator
import uk.gov.hmrc.voabar.services.NdrRules._

import scala.jdk.CollectionConverters._

/**
  * Created by rgallet on 09/12/15.
  */
class RulesCorrectionEngineNdrSpec extends AnyWordSpec with should.Matchers with OptionValues {
  val ebarsValidator = new EbarsValidator

  "RemoveBS7666Addresses" should {
    "remove BS7666Address" in {
      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/RulesCorrectionEngine/NDR_EASTRIDING_BOTH_PROPERTIES.xml")))

      EbarsXmlCutter.getPropertyIdentities(reports).head.getContent.asScala.find (_.getName.getLocalPart == "BS7666Address") should not be(None)

      RemoveBS7666Addresses.apply(reports)

      EbarsXmlCutter.getPropertyIdentities(reports).head.getContent.asScala.find (_.getName.getLocalPart == "BS7666Address") should be(None)
    }
  }

  "PostcodesToUppercase" should {
    "uppercase Postcodes" in {
      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/RulesCorrectionEngine/NDR_EASTRIDING_BOTH_PROPERTIES.xml")))

      val addresses = EbarsXmlCutter.getTextAddressStructures(reports)
      val contactDetails = EbarsXmlCutter.getOccupierContactAddresses(reports)

      addresses should have size(2)
      addresses(0).getPostcode should be("hu13 0pg")
      addresses(1).getPostcode should be("yo15 3qn")

      contactDetails should have size(1)
      contactDetails(0).getPostCode should be("de45 1dl")

      PostcodesToUppercase.apply(reports)

      val addressesAfter = EbarsXmlCutter.getTextAddressStructures(reports)
      addressesAfter should have size(2)
      addressesAfter(0).getPostcode should be("HU13 0PG")
      addressesAfter(1).getPostcode should be("YO15 3QN")

      val contactDetailsAfter = EbarsXmlCutter.getOccupierContactAddresses(reports)
      contactDetailsAfter should have size(1)
      contactDetailsAfter(0).getPostCode should be("DE45 1DL")
    }
  }

  "Rt01AndRt02AndRt03AndRt04MissingProposedEntry" should {
    "have correct codes matching" in {
      Rt01AndRt02AndRt03AndRt04MissingProposedEntry.codes should be(Seq("1", "2", "3", "4"))
    }

    "remove ProposedEntries with 1" in {

      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/RulesCorrectionEngine/NDR_EASTRIDING_RT1_EXISTING_PROPERTIES.xml")))

      Rt01AndRt02AndRt03AndRt04MissingProposedEntry.apply(reports)

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (1)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (0)
    }

    "remove ProposedEntries with 2" in {

      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/RulesCorrectionEngine/NDR_EASTRIDING_RT2_EXISTING_PROPERTIES.xml")))

      Rt01AndRt02AndRt03AndRt04MissingProposedEntry.apply(reports)

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (1)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (0)
    }

    "remove ProposedEntries with 3" in {

      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/RulesCorrectionEngine/NDR_EASTRIDING_RT3_EXISTING_PROPERTIES.xml")))

      Rt01AndRt02AndRt03AndRt04MissingProposedEntry.apply(reports)

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (1)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (0)
    }

    "remove ProposedEntries with 4" in {

      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/RulesCorrectionEngine/NDR_EASTRIDING_RT4_EXISTING_PROPERTIES.xml")))

      Rt01AndRt02AndRt03AndRt04MissingProposedEntry.apply(reports)

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (1)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (0)
    }

    "not change anything" in {

      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/RulesCorrectionEngine/NDR_EASTRIDING_BOTH_PROPERTIES.xml")))

      Rt01AndRt02AndRt03AndRt04MissingProposedEntry.apply(reports)

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (1)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (1)
    }
  }

  "Rt05AndRt06AndRt07AndRt08AndRt09AndRt11MissingExistingEntry" should {
    "have correct codes matching" in {
      Rt05AndRt06AndRt07AndRt08AndRt09AndRt11MissingExistingEntry.codes should be(Seq("5", "6", "7", "8", "9", "11"))
    }

    "remove ProposedEntries with 5" in {

      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/RulesCorrectionEngine/NDR_EASTRIDING_RT5_PROPOSED_PROPERTIES.xml")))

      Rt05AndRt06AndRt07AndRt08AndRt09AndRt11MissingExistingEntry.apply(reports)

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (0)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (1)
    }

    "remove ProposedEntries with 6" in {

      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/RulesCorrectionEngine/NDR_EASTRIDING_RT6_PROPOSED_PROPERTIES.xml")))

      Rt05AndRt06AndRt07AndRt08AndRt09AndRt11MissingExistingEntry.apply(reports)

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (0)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (1)
    }

    "remove ProposedEntries with 7" in {

      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/RulesCorrectionEngine/NDR_EASTRIDING_RT7_PROPOSED_PROPERTIES.xml")))

      Rt05AndRt06AndRt07AndRt08AndRt09AndRt11MissingExistingEntry.apply(reports)

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (0)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (1)
    }

    "remove ProposedEntries with 8" in {

      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/RulesCorrectionEngine/NDR_EASTRIDING_RT8_PROPOSED_PROPERTIES.xml")))

      Rt05AndRt06AndRt07AndRt08AndRt09AndRt11MissingExistingEntry.apply(reports)

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (0)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (1)
    }

    "remove ProposedEntries with 9" in {

      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/RulesCorrectionEngine/NDR_EASTRIDING_RT9_PROPOSED_PROPERTIES.xml")))

      Rt05AndRt06AndRt07AndRt08AndRt09AndRt11MissingExistingEntry.apply(reports)

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (0)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (1)
    }

    "remove ProposedEntries with 11" in {

      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/RulesCorrectionEngine/NDR_EASTRIDING_RT11_PROPOSED_PROPERTIES.xml")))

      Rt05AndRt06AndRt07AndRt08AndRt09AndRt11MissingExistingEntry.apply(reports)

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (0)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (1)
    }
  }

  "Rt05AndRt06AndRt07AndRt08AndRt09AndRt11RemoveProposedEntries" should  {
    "have correct codes matching" in {
      Rt05AndRt06AndRt07AndRt08AndRt09AndRt11RemoveProposedEntries.codes should be(Seq("5", "6", "7", "8", "9", "11"))
    }

    "remove proposed entries" in {
      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/RulesCorrectionEngine/NDR_EASTRIDING_RT5_BOTH_PROPERTIES.xml")))

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (1)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (1)

      Rt05AndRt06AndRt07AndRt08AndRt09AndRt11RemoveProposedEntries.apply(reports)

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (0)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (1)
    }
  }

  "Rt01AndRt02AndRt03AndRt04RemoveExistingEntries" should  {
    "have correct codes matching" in {
      Rt01AndRt02AndRt03AndRt04RemoveExistingEntries.codes should be(Seq("1", "2", "3", "4"))
    }

    "remove proposed entries" in {
      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/RulesCorrectionEngine/NDR_EASTRIDING_RT1_BOTH_PROPERTIES.xml")))

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (1)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (1)

      Rt01AndRt02AndRt03AndRt04RemoveExistingEntries.apply(reports)

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (1)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (0)
    }
  }
}
