/*
 * Copyright 2021 HM Revenue & Customs
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

import ebars.xml.CtaxReasonForReportCodeContentType._
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{Matchers, OptionValues, WordSpecLike}
import services.EbarsValidator
import uk.gov.hmrc.voabar.services.CtRules._

import javax.xml.bind.JAXBElement
import javax.xml.transform.stream.StreamSource
import scala.collection.JavaConverters._

/**
  * Created by rgallet on 09/12/15.
  */
class RulesCorrectionEngineCtSpec extends WordSpecLike with Matchers with OptionValues with TableDrivenPropertyChecks {
  val ebarsValidator = new EbarsValidator

  "RulesCorrectionEngine" should {
    val engine =  new RulesCorrectionEngine

    "ignore NDR files" in {

      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/RulesCorrectionEngine/EASTRIDING_EDITED_NPE.xml")))
      engine.applyRules(reports)
    }

    "CR14 - both entries" in {
      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/RulesCorrectionEngine/CR14_BOTH_PROPERTIES.xml")))
      engine.applyRules(reports)

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (0)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (1)
    }

    "CR14 - only proposed" in {
      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/RulesCorrectionEngine/CR14_PROPOSED_ENTRIES.xml")))
      engine.applyRules(reports)

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (0)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (1)
    }

    "CR14 - only existing" in {
      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/RulesCorrectionEngine/CR14_EXISTING_ENTRIES.xml")))
      engine.applyRules(reports)

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (0)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (1)
    }
  }

  "Cr01AndCr02AndCr06AndCr07AndCr09AndCr10MissingExistingEntry" should {
    "have correct codes matching" in {
      Cr01AndCr02AndCr06AndCr07AndCr09AndCr10AndCr14MissingExistingEntry.codes should be(Seq(CR_01, CR_02, CR_06, CR_07, CR_09, CR_10, CR_14))
    }

    "leaves Proposed and Existing entries alone with CR01" in {

      val reports = ebarsValidator.fromJson(new StreamSource(getClass.getResourceAsStream("/json/RulesCorrectionEngine/Cornwall_CTax_CR01_BothEntries.json")))

      Cr01AndCr02AndCr06AndCr07AndCr09AndCr10AndCr14MissingExistingEntry.apply(reports)

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (1)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (1)
    }

    "leaves Proposed and Existing entries alone with CR02" in {

      val reports = ebarsValidator.fromJson(new StreamSource(getClass.getResourceAsStream("/json/RulesCorrectionEngine/Cornwall_CTax_CR02_BothEntries.json")))

      Cr01AndCr02AndCr06AndCr07AndCr09AndCr10AndCr14MissingExistingEntry.apply(reports)

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (1)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (1)
    }

    "leaves Proposed and Existing entries alone with CR06" in {

      val reports = ebarsValidator.fromJson(new StreamSource(getClass.getResourceAsStream("/json/RulesCorrectionEngine/Cornwall_CTax_CR06_BothEntries.json")))

      Cr01AndCr02AndCr06AndCr07AndCr09AndCr10AndCr14MissingExistingEntry.apply(reports)

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (1)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (1)
    }

    "leaves Proposed and Existing entries alone with CR07" in {

      val reports = ebarsValidator.fromJson(new StreamSource(getClass.getResourceAsStream("/json/RulesCorrectionEngine/Cornwall_CTax_CR07_BothEntries.json")))

      Cr01AndCr02AndCr06AndCr07AndCr09AndCr10AndCr14MissingExistingEntry.apply(reports)

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (1)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (1)
    }

    "leaves Proposed and Existing entries alone with CR09" in {

      val reports = ebarsValidator.fromJson(new StreamSource(getClass.getResourceAsStream("/json/RulesCorrectionEngine/Cornwall_CTax_CR09_BothEntries.json")))

      Cr01AndCr02AndCr06AndCr07AndCr09AndCr10AndCr14MissingExistingEntry.apply(reports)

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (1)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (1)
    }

    "leaves Proposed and Existing entries alone with CR10" in {

      val reports = ebarsValidator.fromJson(new StreamSource(getClass.getResourceAsStream("/json/RulesCorrectionEngine/Cornwall_CTax_CR10_BothEntries.json")))

      Cr01AndCr02AndCr06AndCr07AndCr09AndCr10AndCr14MissingExistingEntry.apply(reports)

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (1)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (1)
    }

    "leaves Proposed and Existing entries alone with CR14" in {

      val reports = ebarsValidator.fromJson(new StreamSource(getClass.getResourceAsStream("/json/RulesCorrectionEngine/Cornwall_CTax_CR14_BothEntries.json")))

      Cr01AndCr02AndCr06AndCr07AndCr09AndCr10AndCr14MissingExistingEntry.apply(reports)

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (1)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (1)
    }

    "use Proposed Entries with CR01" in {

      val reports = ebarsValidator.fromJson(new StreamSource(getClass.getResourceAsStream("/json/RulesCorrectionEngine/Cornwall_CTax_CR01_ProposedEntries.json")))

      Cr01AndCr02AndCr06AndCr07AndCr09AndCr10AndCr14MissingExistingEntry.apply(reports)

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (0)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (1)
    }

    "use Proposed Entries with CR06" in {

      val reports = ebarsValidator.fromJson(new StreamSource(getClass.getResourceAsStream("/json/RulesCorrectionEngine/Cornwall_CTax_CR06_ProposedEntries.json")))

      Cr01AndCr02AndCr06AndCr07AndCr09AndCr10AndCr14MissingExistingEntry.apply(reports)

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (0)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (1)
    }

    "use Proposed Entries with CR07" in {

      val reports = ebarsValidator.fromJson(new StreamSource(getClass.getResourceAsStream("/json/RulesCorrectionEngine/Cornwall_CTax_CR07_ProposedEntries.json")))

      Cr01AndCr02AndCr06AndCr07AndCr09AndCr10AndCr14MissingExistingEntry.apply(reports)

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (0)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (1)
    }

    "use Proposed Entries with CR09" in {

      val reports = ebarsValidator.fromJson(new StreamSource(getClass.getResourceAsStream("/json/RulesCorrectionEngine/Cornwall_CTax_CR09_ProposedEntries.json")))

      Cr01AndCr02AndCr06AndCr07AndCr09AndCr10AndCr14MissingExistingEntry.apply(reports)

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (0)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (1)
    }

    "use Proposed Entries with CR10" in {

      val reports = ebarsValidator.fromJson(new StreamSource(getClass.getResourceAsStream("/json/RulesCorrectionEngine/Cornwall_CTax_CR10_ProposedEntries.json")))

      Cr01AndCr02AndCr06AndCr07AndCr09AndCr10AndCr14MissingExistingEntry.apply(reports)

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (0)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (1)
    }

    "remove ProposedEntries with CR02 from xml" in {

      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/CARDIFF_EDITED_CRCD_RMRKS.xml")))

      Cr01AndCr02AndCr06AndCr07AndCr09AndCr10AndCr14MissingExistingEntry.apply(reports)

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (0)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (1)
    }

    "remove ProposedEntries with CR14 from xml" in {

      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/RulesCorrectionEngine/CR14_PROPOSED_ENTRIES.xml")))

      Cr01AndCr02AndCr06AndCr07AndCr09AndCr10AndCr14MissingExistingEntry.apply(reports)

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (0)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (1)
    }

    "not change anything" in {

      val reports = ebarsValidator.fromJson(new StreamSource(getClass.getResourceAsStream("/json/RulesCorrectionEngine/Cornwall_CTax_CR03_BothEntries.json")))

      Cr01AndCr02AndCr06AndCr07AndCr09AndCr10AndCr14MissingExistingEntry.apply(reports)

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (1)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (1)
    }
  }

  "Cr01AndCr02AndCr06AndCr07AndCr09AndCr10AndCr14RemoveProposedEntries" should {
    "have correct codes matching" in {
      Cr01AndCr02AndCr06AndCr07AndCr09AndCr10AndCr14RemoveProposedEntries.codes should be(Seq(CR_01, CR_02, CR_06, CR_07, CR_09, CR_10, CR_14))
    }

    "remove ProposedEntries with CR01" in {

      val reports = ebarsValidator.fromJson(new StreamSource(getClass.getResourceAsStream("/json/RulesCorrectionEngine/Cornwall_CTax_CR01_BothEntries.json")))

      Cr01AndCr02AndCr06AndCr07AndCr09AndCr10AndCr14RemoveProposedEntries.apply(reports)

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (0)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (1)
    }

    "remove ProposedEntries with CR14" in {

      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/RulesCorrectionEngine/CR14_BOTH_PROPERTIES.xml")))

      Cr01AndCr02AndCr06AndCr07AndCr09AndCr10AndCr14RemoveProposedEntries.apply(reports)

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (0)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (1)
    }

    "not change anything" in {

      val reports = ebarsValidator.fromJson(new StreamSource(getClass.getResourceAsStream("/json/RulesCorrectionEngine/Cornwall_CTax_CR03_BothEntries.json")))

      Cr01AndCr02AndCr06AndCr07AndCr09AndCr10AndCr14RemoveProposedEntries.apply(reports)

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (1)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (1)
    }
  }

  "Cr03AndCr04BothProposedAndExistingEntries" should {
    "have correct codes matching" in {
      Cr03AndCr04BothProposedAndExistingEntries.codes should be(Seq(CR_03, CR_04))
    }

    "remove ProposedEntries with CR01" in {

      val reports = ebarsValidator.fromJson(new StreamSource(getClass.getResourceAsStream("/json/RulesCorrectionEngine/Cornwall_CTax_CR03_BothEntries.json")))

      Cr03AndCr04BothProposedAndExistingEntries.apply(reports)

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (1)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (0)
    }

    "not change anything" in {

      val reports = ebarsValidator.fromJson(new StreamSource(getClass.getResourceAsStream("/json/RulesCorrectionEngine/Cornwall_CTax_CR01_BothEntries.json")))

      Cr03AndCr04BothProposedAndExistingEntries.apply(reports)

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (1)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (1)
    }
  }

  "Cr03AndCr04MissingProposedEntry" should {
    "have correct codes matching" in {
      Cr03AndCr04MissingProposedEntry.codes should be(Seq(CR_03, CR_04))
    }

    "remove ProposedEntries with CR01" in {

      val reports = ebarsValidator.fromJson(new StreamSource(getClass.getResourceAsStream("/json/RulesCorrectionEngine/Cornwall_CTax_CR03_ExistingEntry.json")))

      Cr03AndCr04MissingProposedEntry.apply(reports)

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (1)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (0)
    }

    "not change anything" in {

      val reports = ebarsValidator.fromJson(new StreamSource(getClass.getResourceAsStream("/json/RulesCorrectionEngine/Cornwall_CTax_CR01_BothEntries.json")))

      Cr03AndCr04MissingProposedEntry.apply(reports)

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (1)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (1)
    }
  }

  "Cr05AndCr12MissingProposedEntry" should {
    "have correct codes matching" in {
      Cr05AndCr12MissingAnyEntry.codes should be(Seq(CR_05, CR_12))
    }

    "copy ProposedEntries with CR05" in {

      val reports = ebarsValidator.fromJson(new StreamSource(getClass.getResourceAsStream("/json/RulesCorrectionEngine/Cornwall_CTax_CR05_ExistingEntry.json")))

      Cr05AndCr12MissingAnyEntry.apply(reports)

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (1)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (1)
    }

    "copy ExistingEntry with CR05" in {

      val reports = ebarsValidator.fromJson(new StreamSource(getClass.getResourceAsStream("/json/RulesCorrectionEngine/Cornwall_CTax_CR05_ProposedEntry.json")))

      Cr05AndCr12MissingAnyEntry.apply(reports)

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (1)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (1)
    }

    "not change anything" in {

      val reports = ebarsValidator.fromJson(new StreamSource(getClass.getResourceAsStream("/json/RulesCorrectionEngine/Cornwall_CTax_CR01_BothEntries.json")))

      Cr05AndCr12MissingAnyEntry.apply(reports)

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (1)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (1)
    }
  }

  "PostcodesToUppercase" should {
    "uppercase postcodes" in {

      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/CARDIFF_EDITED_CRCD_RMRKS_WITH_SUFFIX.xml")))

      PostcodesToUppercase.apply(reports)

      EbarsXmlCutter.OccupierContactAddresses(reports) foreach { occupierContactAddress =>
        occupierContactAddress.getPostCode should be("CF24 0EF")
      }

      EbarsXmlCutter.TextAddressStructures(reports) foreach { textAddressStructure =>
        textAddressStructure.getPostcode should be("CF24 0EF")
      }
    }
  }

  "RemarksTrimmer" should {
    "remove all whitespace at beginning and end" in {

      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/RulesCorrectionEngine/REMARKS_WITH_WHITESPACE.xml")))

      EbarsXmlCutter.findRemarksIdx(reports) should have size (1)

      RemarksTrimmer.apply(reports)

      val indices = EbarsXmlCutter.findRemarksIdx(reports)

      indices should have size (1)
      val remarks = reports.getBApropertyReport.get(0).getContent.get(indices(0))

      remarks.getValue.asInstanceOf[String] should be("THIS IS A BLUEPRINT TEST PLEASE DELETE / NO ACTION THIS REPORT")
    }

    "remove obscure space/whitespace characters and replace multiple space characters with one" in {

      val testData = Table(
        ("original string", "expected result"),
        ("""This is test with  space and      space""", "This is test with space and space"),
        (
          """This is string with multiple space
            | and new line""".stripMargin, "This is string with multiple space and new line"),
        ("simple \u00A0space", "simple space"),
        ("simple\u00A0space", "simple space"),
        ("simple\u2005\u1680space", "simple space"),
        ("simple\u3000 \u2029 \nspace", "simple space"),
        ("simple\nspace", "simple space"),
      )

      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/RulesCorrectionEngine/REMARKS_WITH_WHITESPACE.xml")))

      EbarsXmlCutter.findRemarksIdx(reports) should have size (1)

      val indices = EbarsXmlCutter.findRemarksIdx(reports)
      val remarks = reports.getBApropertyReport.get(0).getContent.get(indices(0)).asInstanceOf[JAXBElement[String]]

      forAll(testData) { (originalString: String, expectedResult: String) =>

        remarks.setValue(originalString)

        RemarksTrimmer.apply(reports)

        remarks.getValue should be(expectedResult)
      }

    }

    "Throw assertion error for submissions with more that one report" in {
      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/CTValid2.xml")))
      assertThrows[AssertionError] {
        RemarksTrimmer.apply(reports)
      }
    }
  }

  "RemarksFillDefault" should {
    "leave it alone if present" in {

      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/CARDIFF_EDITED_CRCD_RMRKS_WITH_SUFFIX.xml")))

      EbarsXmlCutter.findRemarksIdx(reports) should have size (1)

      RemarksFillDefault.apply(reports)

      val indices = EbarsXmlCutter.findRemarksIdx(reports)

      indices should have size (1)
      val remarks = reports.getBApropertyReport.get(0).getContent.get(indices(0))

      remarks.getValue.asInstanceOf[String] should be("THIS IS A BLUEPRINT TEST.PLEASE DELETE/NO ACTION THIS REPORT")
    }

    "fill with default if empty" in {

      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/CARDIFF_EDITED_CRCD_RMRKS_EMPTY_REMARKS.xml")))

      EbarsXmlCutter.findRemarksIdx(reports) should have size (1)

      RemarksFillDefault.apply(reports)

      val indices = EbarsXmlCutter.findRemarksIdx(reports)

      indices should have size (1)
      val remarks = reports.getBApropertyReport.get(0).getContent.get(indices(0))

      remarks.getValue.asInstanceOf[String] should be("NO REMARKS")
    }

    "fill with default if missing" in {

      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/CARDIFF_EDITED_CRCD_RMRKS_MISSING_REMARKS.xml")))

      EbarsXmlCutter.findRemarksIdx(reports) should have size (0)

      RemarksFillDefault.apply(reports)

      val indices = EbarsXmlCutter.findRemarksIdx(reports)

      indices should have size (1)
      val remarks = reports.getBApropertyReport.get(0).getContent.get(indices(0))

      remarks.getValue.asInstanceOf[String] should be("NO REMARKS")
    }
  }

  "RemovingInvalidTaxBand" should {
    "return C" in {

      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/BEXLEY_UNEDITED.xml")))

      EbarsXmlCutter.CurrentTax(reports) should have size (3)

      RemovingInvalidTaxBand.apply(reports)

      EbarsXmlCutter.CurrentTax(reports) should have size (3)
    }

    "remove if invalid" in {

      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/BEXLEY_UNEDITED_INVALID_TAX_BAND.xml")))

      EbarsXmlCutter.CurrentTax(reports) should have size (1)

      RemovingInvalidTaxBand.apply(reports)

      EbarsXmlCutter.CurrentTax(reports) should have size (0)
    }
  }

  "PropertyDescriptionTextRemoval" must {
    "valid property description" in {

      val reports = ebarsValidator.fromJson(new StreamSource(getClass.getResourceAsStream("/json/RulesCorrectionEngine/Cornwall_CTax_CR08_BothEntries.json")))

      EbarsXmlCutter.PropertyDescriptions(reports) should have size (1)
      EbarsXmlCutter.PropertyDescriptions(reports)(0).getPropertyDescriptionText should be("valid length")

      PropertyDescriptionTextRemoval.apply(reports)

      EbarsXmlCutter.PropertyDescriptions(reports)(0).getPropertyDescriptionText should be("valid length")
    }

    "invalid property description - too short" in {

      val reports = ebarsValidator.fromJson(new StreamSource(getClass.getResourceAsStream("/json/RulesValidationEngine/Cornwall_CTax_InvalidStreetDescription2.json")))

      EbarsXmlCutter.PropertyDescriptions(reports) should have size (1)
      EbarsXmlCutter.PropertyDescriptions(reports)(0).getPropertyDescriptionText should be("B")

      PropertyDescriptionTextRemoval.apply(reports)

      EbarsXmlCutter.PropertyDescriptions(reports) should have size (0)
    }
  }

  "Cr05CopyProposedEntriesToExistingEntries" should {
    "have correct codes matching" in {
      Cr05CopyProposedEntriesToExistingEntries.codes should be(Seq(CR_05))
    }

    "have copied proposed entries to existing with prefix" in {

      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/RulesCorrectionEngine/CR05_BOTH_PROPERTIES.xml")))

      val textAddressStructures = EbarsXmlCutter.TextAddressStructures(reports)
      textAddressStructures should have size (2)

      Cr05CopyProposedEntriesToExistingEntries.apply(reports)

      val textAddressStructuresAfter = EbarsXmlCutter.TextAddressStructures(reports)
      textAddressStructuresAfter should have size (3)

      textAddressStructuresAfter(1).getAddressLine.get(0) should be("[PROPOSED] ROMAIN - GROUND FLOOR FLAT")
      textAddressStructuresAfter(1).getAddressLine.get(1) should be("[PROPOSED] ROMAIN - 11 RUBY STREET")
      textAddressStructuresAfter(1).getAddressLine.get(2) should be("[PROPOSED] ROMAIN - ADAMSDOWN")
      textAddressStructuresAfter(1).getAddressLine.get(3) should be("[PROPOSED] ROMAIN - CARDIFF")
      textAddressStructuresAfter(1).getPostcode should be("CF24 1LP")
    }
  }

  "Cr12CopyProposedEntriesToRemarks" should {
    "have correct codes matching" in {
      Cr12CopyProposedEntriesToRemarks.codes should be(Seq(CR_12))
    }

    "have copied proposed entries to existing with prefix" in {

      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/RulesCorrectionEngine/CR12_BOTH_PROPERTIES.xml")))

      val textAddressStructures = EbarsXmlCutter.TextAddressStructures(reports)
      textAddressStructures should have size (2)

      Cr12CopyProposedEntriesToRemarks.apply(reports)

      val textAddressStructuresAfter = EbarsXmlCutter.TextAddressStructures(reports)
      textAddressStructuresAfter should have size (2)

      EbarsXmlCutter.Remarks(reports) should contain("THIS IS A BLUEPRINT TEST.PLEASE DELETE/NO ACTION THIS REPORT - [PROPOSED] - [ROMAIN - GROUND FLOOR FLAT,ROMAIN - 11 RUBY STREET,ROMAIN - ADAMSDOWN,ROMAIN - CARDIFF,CF24 1LP]")
    }
  }

  "RemoveBS7666Addresses" should {
    "remove BS7666Address" in {
      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/RulesValidationEngine/CARDIFF_EDITED_CRCD_RMRKS_BOTH_PROPERTIES.xml")))

      EbarsXmlCutter.PropertyIdentities(reports).head.getContent.asScala.find (_.getName.getLocalPart == "BS7666Address") should not be(None)

      RemoveBS7666Addresses.apply(reports)

      EbarsXmlCutter.PropertyIdentities(reports).head.getContent.asScala.find (_.getName.getLocalPart == "BS7666Address") should be(None)
    }
  }

  "RemovePropertyGridCoords" should {
    "remove PropertyGridCoords" in {
      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/RulesValidationEngine/CARDIFF_EDITED_CRCD_RMRKS_BOTH_PROPERTIES.xml")))

      EbarsXmlCutter.PropertyIdentities(reports).head.getContent.asScala.find (_.getName.getLocalPart == "PropertyGridCoords") should not be(None)

      RemovePropertyGridCoords.apply(reports)

      EbarsXmlCutter.PropertyIdentities(reports).head.getContent.asScala.find (_.getName.getLocalPart == "PropertyGridCoords") should be(None)
    }
  }
}
