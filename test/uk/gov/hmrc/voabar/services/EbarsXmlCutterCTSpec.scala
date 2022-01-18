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

import ebars.xml.{BandType, CtaxReasonForReportCodeContentType}
import org.scalatest.{Matchers, OptionValues, WordSpecLike}
import services.EbarsValidator

import javax.xml.transform.stream.StreamSource
import scala.collection.JavaConverters._

/**
  * Created by rgallet on 09/12/15.
  */
class EbarsXmlCutterCTSpec extends WordSpecLike with Matchers with OptionValues {
  val ebarsValidator = new EbarsValidator

  "extracting CR code" should {
    "return CR03" in {
      val reports = ebarsValidator.fromJson(new StreamSource(getClass.getResourceAsStream("/json/Cornwall_CTax_Valid_BothEntries.json")))

      EbarsXmlCutter.CR(reports) should contain(CtaxReasonForReportCodeContentType.CR_03)
    }

    "return None if invalid" in {
      val reports = ebarsValidator.fromJson(new StreamSource(getClass.getResourceAsStream("/json/RulesCorrectionEngine/Cornwall_CTax_MissingCRinFrontOfCrCode.json")))

      EbarsXmlCutter.CR(reports) should be(None)
    }
  }

  "working from a files with both entries" should {
    "remove all proposed entries and move the first one to existing" in {
      val reports = ebarsValidator.fromJson(new StreamSource(getClass.getResourceAsStream("/json/Cornwall_CTax_Valid_BothEntries.json")))

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (1)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (1)

      EbarsXmlCutter.convertProposedEntriesIntoExistingEntries(reports)

      EbarsXmlCutter.findLastTypeOfTaxIdx(reports) should contain(4)

      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (1)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should contain(5)

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (0)
    }

    "copy first proposed entry to existing" in {
      val reports = ebarsValidator.fromJson(new StreamSource(getClass.getResourceAsStream("/json/Cornwall_CTax_Valid_BothEntries.json")))

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (1)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (1)

      EbarsXmlCutter.copyProposedEntriesToExisting(reports)

      EbarsXmlCutter.findLastTypeOfTaxIdx(reports) should contain(4)

      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (1)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should contain(5)

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (1)
      EbarsXmlCutter.findProposedEntriesIdx(reports) should contain(6)
    }
  }

  "working from a file with just a proposed entry" should {
    "remove all proposed entries and move the first one to existing" in {
      val reports = ebarsValidator.fromJson(new StreamSource(getClass.getResourceAsStream("/json/Cornwall_CTax_Valid_ProposedEntries.json")))

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (1)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (0)

      EbarsXmlCutter.convertProposedEntriesIntoExistingEntries(reports)

      EbarsXmlCutter.findLastTypeOfTaxIdx(reports) should contain(4)
      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (0)

      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (1)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should contain(5)
    }

    "copy first proposed entry to existing" in {
      val reports = ebarsValidator.fromJson(new StreamSource(getClass.getResourceAsStream("/json/Cornwall_CTax_Valid_ProposedEntries.json")))

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (1)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (0)

      EbarsXmlCutter.copyProposedEntriesToExisting(reports)

      EbarsXmlCutter.findLastTypeOfTaxIdx(reports) should contain(4)

      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (1)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should contain(5)

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (1)
      EbarsXmlCutter.findProposedEntriesIdx(reports) should contain(6)

      val textAddressStructuresAfter = EbarsXmlCutter.TextAddressStructures(reports)
      textAddressStructuresAfter should have size (2)

      textAddressStructuresAfter(0).getAddressLine.get(0) should be("4 NICKLEBY COURT")
      textAddressStructuresAfter(0).getAddressLine.get(1) should be("LISKEARD")
      textAddressStructuresAfter(0).getAddressLine.get(2) should be("CORNWALL")
      textAddressStructuresAfter(0).getPostcode should be("PL14 3FP")

      val baReferencesAfter = EbarsXmlCutter.BAreferences(reports)
      baReferencesAfter should have size (2)
      baReferencesAfter(0) should be("11010635004000")
      baReferencesAfter(1) should be("11010635004000")
    }

    "copy first existing entry to proposed - existing entry" in {
      val reports = ebarsValidator.fromJson(new StreamSource(getClass.getResourceAsStream("/json/Cornwall_CTax_Valid_ProposedEntries.json")))

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (1)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (0)

      EbarsXmlCutter.copyExistingEntriesToProposed(reports)

      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (0)
      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (0)
    }

    "move first existing entry to proposed - removes all entries" in {
      val reports = ebarsValidator.fromJson(new StreamSource(getClass.getResourceAsStream("/json/Cornwall_CTax_Valid_ProposedEntries.json")))

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (1)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (0)

      EbarsXmlCutter.convertExistingEntriesIntoProposedEntries(reports)

      EbarsXmlCutter.findLastTypeOfTaxIdx(reports) should contain(4)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (0)
      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (0)
    }
  }

  "working from a file with just an existing entry" should {
    "remove all proposed entries and move the first one to existing" in {
      val reports = ebarsValidator.fromJson(new StreamSource(getClass.getResourceAsStream("/json/Cornwall_CTax_Valid_ExistingEntries.json")))

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (0)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (1)

      EbarsXmlCutter.convertProposedEntriesIntoExistingEntries(reports)

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (0)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (0)
    }

    "copy first proposed entry to existing - no proposed entry" in {
      val reports = ebarsValidator.fromJson(new StreamSource(getClass.getResourceAsStream("/json/Cornwall_CTax_Valid_ExistingEntries.json")))

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (0)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (1)

      EbarsXmlCutter.copyProposedEntriesToExisting(reports)

      EbarsXmlCutter.findLastTypeOfTaxIdx(reports) should contain(4)

      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (0)
      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (0)
    }

    "copy first existing entry to proposed" in {
      val reports = ebarsValidator.fromJson(new StreamSource(getClass.getResourceAsStream("/json/Cornwall_CTax_Valid_ExistingEntries.json")))

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (0)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (1)

      EbarsXmlCutter.copyExistingEntriesToProposed(reports)

      EbarsXmlCutter.findLastTypeOfTaxIdx(reports) should contain(4)

      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (1)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should contain(5)

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (1)
      EbarsXmlCutter.findProposedEntriesIdx(reports) should contain(6)

      val textAddressStructuresAfter = EbarsXmlCutter.TextAddressStructures(reports)
      textAddressStructuresAfter should have size (2)

      textAddressStructuresAfter(0).getAddressLine.get(0) should be("MANAGERS ACCOM. THE BARBICAN INN")
      textAddressStructuresAfter(0).getAddressLine.get(1) should be("BARBICAN ROAD")
      textAddressStructuresAfter(0).getAddressLine.get(2) should be("EAST LOOE")
      textAddressStructuresAfter(0).getAddressLine.get(3) should be("CORNWALL")
      textAddressStructuresAfter(0).getPostcode should be("PL13 1EY")

      val baReferencesAfter = EbarsXmlCutter.BAreferences(reports)
      baReferencesAfter should have size (2)
      baReferencesAfter(0) should be("11031818521880")
      baReferencesAfter(1) should be("11031818521880")
    }

    "move first existing entry to proposed" in {
      val reports = ebarsValidator.fromJson(new StreamSource(getClass.getResourceAsStream("/json/Cornwall_CTax_Valid_ExistingEntries.json")))

      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (0)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (1)

      EbarsXmlCutter.convertExistingEntriesIntoProposedEntries(reports)

      EbarsXmlCutter.findLastTypeOfTaxIdx(reports) should contain(4)
      EbarsXmlCutter.findExistingEntriesIdx(reports) should have size (0)
      EbarsXmlCutter.findProposedEntriesIdx(reports) should have size (1)
      EbarsXmlCutter.findProposedEntriesIdx(reports) should contain(5)
    }
  }

  "extracting OccupierContact" should {
    "return values from all OccupierContact" in {
      val reports = ebarsValidator.fromJson(new StreamSource(getClass.getResourceAsStream("/json/Cornwall_CTax_Valid_ExistingEntries.json")))

      EbarsXmlCutter.OccupierContacts(reports) should have size (1)
      EbarsXmlCutter.OccupierContacts(reports)(0).getOccupierName.getPersonFamilyName should be("FINNIMORE")
      EbarsXmlCutter.OccupierContacts(reports)(0).getOccupierName.getPersonRequestedName should be("MR M FINNIMORE")
      EbarsXmlCutter.OccupierContacts(reports)(0).getOccupierName.getPersonGivenName().get(0) should be("M")
      EbarsXmlCutter.OccupierContacts(reports)(0).getOccupierName.getPersonNameTitle().get(0) should be("MR")
      EbarsXmlCutter.OccupierContacts(reports)(0).getOccupierName.getPersonNameSuffix().get(0) should be("P")
    }

    "return values from 1 AssessmentProperties" in {
      val reports = ebarsValidator.fromJson(new StreamSource(getClass.getResourceAsStream("/json/Cornwall_CTax_Valid_ExistingEntries.json")))
      val assessmentProperties = EbarsXmlCutter.AssessmentProperties(reports)(0)

      EbarsXmlCutter.OccupierContacts(assessmentProperties).get.getOccupierName.getPersonFamilyName should be("FINNIMORE")
      EbarsXmlCutter.OccupierContacts(assessmentProperties).get.getOccupierName.getPersonRequestedName should be("MR M FINNIMORE")
      EbarsXmlCutter.OccupierContacts(assessmentProperties).get.getOccupierName.getPersonGivenName().get(0) should be("M")
      EbarsXmlCutter.OccupierContacts(assessmentProperties).get.getOccupierName.getPersonNameTitle().get(0) should be("MR")
      EbarsXmlCutter.OccupierContacts(assessmentProperties).get.getOccupierName.getPersonNameSuffix().get(0) should be("P")
    }
  }

  "extracting postcodes" should {
    "return values from TextAddress" in {
      val reports = ebarsValidator.fromJson(new StreamSource(getClass.getResourceAsStream("/json/Cornwall_CTax_Valid_ExistingEntries.json")))

      EbarsXmlCutter.TextAddressStructures(reports) should have size (1)
      EbarsXmlCutter.TextAddressStructures(reports)(0).getPostcode should be("PL13 1EY")
    }

    "return values from TextAddress - no postcode" in {
      val reports = ebarsValidator.fromJson(new StreamSource(getClass.getResourceAsStream("/json/Cornwall_CTax_OccupierContact_NoPostcode.json")))

      EbarsXmlCutter.TextAddressStructures(reports) should have size (1)
      EbarsXmlCutter.TextAddressStructures(reports)(0).getPostcode should be(null)
    }

    "return values from OccupierContact" in {
      val reports = ebarsValidator.fromJson(new StreamSource(getClass.getResourceAsStream("/json/Cornwall_CTax_Valid_ExistingEntries.json")))

      EbarsXmlCutter.OccupierContactAddresses(reports) should have size (1)
      EbarsXmlCutter.OccupierContactAddresses(reports)(0).getPostCode should be("PL13 1EY")
    }

    "return values from OccupierContact - no postcode" in {
      val reports = ebarsValidator.fromJson(new StreamSource(getClass.getResourceAsStream("/json/Cornwall_CTax_OccupierContact_NoPostcode.json")))

      EbarsXmlCutter.OccupierContactAddresses(reports) should have size (1)
      EbarsXmlCutter.OccupierContactAddresses(reports)(0).getPostCode should be(null)
    }

    "return values from OccupierContact - no OccupierContact" in {
      val reports = ebarsValidator.fromJson(new StreamSource(getClass.getResourceAsStream("/json/Cornwall_CTax_OccupierContact_NoOccupierDetails.json")))

      EbarsXmlCutter.OccupierContactAddresses(reports) should have size (0)
    }
  }

  "extracting remarks " should {
    "return all suffices" in {
      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/CARDIFF_EDITED_CRCD_RMRKS_WITH_SUFFIX.xml")))

      EbarsXmlCutter.findRemarksIdx(reports) should have size (1)
      EbarsXmlCutter.findRemarksIdx(reports) should contain(7)
      EbarsXmlCutter.Remarks(reports) should contain("THIS IS A BLUEPRINT TEST.PLEASE DELETE/NO ACTION THIS REPORT")
    }
  }

  "extracting council tax band" should {
    "return C" in {
      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/BEXLEY_UNEDITED.xml")))

      EbarsXmlCutter.CurrentTax(reports) should have size (3)
      EbarsXmlCutter.CurrentTax(reports)(0).getCouncilTaxBand should be(BandType.A)
      EbarsXmlCutter.CurrentTax(reports)(1).getCouncilTaxBand should be(BandType.B)
      EbarsXmlCutter.CurrentTax(reports)(2).getCouncilTaxBand should be(BandType.C)
    }

    "return null if invalid" in {
      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/BEXLEY_UNEDITED_INVALID_TAX_BAND.xml")))

      EbarsXmlCutter.CurrentTax(reports) should have size (1)
      EbarsXmlCutter.CurrentTax(reports)(0).getCouncilTaxBand should be(null)
    }

    "return null if missing" in {
      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/BEXLEY_UNEDITED_NO_TAX_BAND.xml")))

      EbarsXmlCutter.CurrentTax(reports) should have size (0)
    }

    "do nothing if already missing" in {
      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/BEXLEY_UNEDITED_NO_TAX_BAND.xml")))

      EbarsXmlCutter.CurrentTax(reports) should have size (0)
      EbarsXmlCutter.removeNullCurrentTax(reports)
      EbarsXmlCutter.CurrentTax(reports) should have size (0)
    }

    "remove if invalid" in {
      val ebarsValidator = new EbarsValidator
      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/BEXLEY_UNEDITED_INVALID_TAX_BAND.xml")))

      EbarsXmlCutter.CurrentTax(reports) should have size (1)

      EbarsXmlCutter.removeNullCurrentTax(reports)

      EbarsXmlCutter.CurrentTax(reports) should have size (0)
    }

    "do nothing if valid" in {
      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/BEXLEY_UNEDITED.xml")))

      EbarsXmlCutter.CurrentTax(reports) should have size (3)
      EbarsXmlCutter.removeNullCurrentTax(reports)
      EbarsXmlCutter.CurrentTax(reports) should have size (3)
    }
  }

  "PropertyDescriptions" should {
    "find both descriptions" in {
      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/RulesValidationEngine/CARDIFF_EDITED_CRCD_RMRKS_BOTH_PROPERTIES.xml")))

      val propertyDescriptions = EbarsXmlCutter.PropertyDescriptions(reports)

      propertyDescriptions(0).getPropertyDescriptionText should be("Not Known")
      propertyDescriptions(1).getPropertyDescriptionText should be("Not Known")
    }
  }

  "Remarks" should {
    "find Remarks" in {
      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/RulesValidationEngine/CARDIFF_EDITED_CRCD_RMRKS_BOTH_PROPERTIES.xml")))

      EbarsXmlCutter.Remarks(reports).head should be("THIS IS A BLUEPRINT TEST.PLEASE DELETE/NO ACTION THIS REPORT")
    }
  }

  "PropertyPlanReferenceNumber" should {
    "find PropertyPlanReferenceNumber" in {
      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/RulesValidationEngine/CARDIFF_EDITED_CRCD_RMRKS_BOTH_PROPERTIES.xml")))

      EbarsXmlCutter.PropertyPlanReferenceNumber(reports).head should be("43242432432")
    }
  }

  "appendProposedEntriesToExisting" should {
    "append proposed to existing" in {
      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/RulesValidationEngine/CARDIFF_EDITED_CRCD_RMRKS_BOTH_PROPERTIES.xml")))

      val textAddressStructures = EbarsXmlCutter.TextAddressStructures(reports)
      textAddressStructures should have size (2)

      textAddressStructures(0).getAddressLine.get(0) should be("GROUND FLOOR FLAT")
      textAddressStructures(0).getAddressLine.get(1) should be("11 RUBY STREET")
      textAddressStructures(0).getAddressLine.get(2) should be("ADAMSDOWN")
      textAddressStructures(0).getAddressLine.get(3) should be("CARDIFF")
      textAddressStructures(0).getPostcode should be("CF24 1LP")

      textAddressStructures(1).getAddressLine.get(0) should be("ROMAIN - GROUND FLOOR FLAT")
      textAddressStructures(1).getAddressLine.get(1) should be("ROMAIN - 11 RUBY STREET")
      textAddressStructures(1).getAddressLine.get(2) should be("ROMAIN - ADAMSDOWN")
      textAddressStructures(1).getAddressLine.get(3) should be("ROMAIN - CARDIFF")
      textAddressStructures(1).getPostcode should be("CF24 1LP")

      EbarsXmlCutter.appendProposedEntriesToExisting(reports)
      val textAddressStructuresAfter = EbarsXmlCutter.TextAddressStructures(reports)
      textAddressStructuresAfter should have size (3)

      textAddressStructuresAfter(0).getAddressLine.get(0) should be("GROUND FLOOR FLAT")
      textAddressStructuresAfter(0).getAddressLine.get(1) should be("11 RUBY STREET")
      textAddressStructuresAfter(0).getAddressLine.get(2) should be("ADAMSDOWN")
      textAddressStructuresAfter(0).getAddressLine.get(3) should be("CARDIFF")
      textAddressStructuresAfter(0).getPostcode should be("CF24 1LP")

      textAddressStructuresAfter(1).getAddressLine.get(0) should be("[PROPOSED] ROMAIN - GROUND FLOOR FLAT")
      textAddressStructuresAfter(1).getAddressLine.get(1) should be("[PROPOSED] ROMAIN - 11 RUBY STREET")
      textAddressStructuresAfter(1).getAddressLine.get(2) should be("[PROPOSED] ROMAIN - ADAMSDOWN")
      textAddressStructuresAfter(1).getAddressLine.get(3) should be("[PROPOSED] ROMAIN - CARDIFF")
      textAddressStructuresAfter(1).getPostcode should be("CF24 1LP")

      textAddressStructuresAfter(2).getAddressLine.get(0) should be("ROMAIN - GROUND FLOOR FLAT")
      textAddressStructuresAfter(2).getAddressLine.get(1) should be("ROMAIN - 11 RUBY STREET")
      textAddressStructuresAfter(2).getAddressLine.get(2) should be("ROMAIN - ADAMSDOWN")
      textAddressStructuresAfter(2).getAddressLine.get(3) should be("ROMAIN - CARDIFF")
      textAddressStructuresAfter(2).getPostcode should be("CF24 1LP")

      val occupierContactsAfter = EbarsXmlCutter.OccupierContacts(reports)
      occupierContactsAfter should have size (2)

      occupierContactsAfter(0).getOccupierName.getPersonFamilyName should be("MALIK")
      occupierContactsAfter(0).getOccupierName.getPersonGivenName().get(0) should be("MOHAMMED")
      occupierContactsAfter(0).getOccupierName.getPersonNameTitle().get(0) should be("MR")

      occupierContactsAfter(1).getOccupierName.getPersonFamilyName should be("MALIK")
      occupierContactsAfter(1).getOccupierName.getPersonGivenName().get(0) should be("MOHAMMED")
      occupierContactsAfter(1).getOccupierName.getPersonNameTitle().get(0) should be("MR")
    }
  }

  "appendProposedEntriesToRemarks" should {
    "append proposed to remarks" in {
      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/RulesValidationEngine/CARDIFF_EDITED_CRCD_RMRKS_BOTH_PROPERTIES.xml")))

      val textAddressStructures = EbarsXmlCutter.TextAddressStructures(reports)
      textAddressStructures should have size (2)

      textAddressStructures(0).getAddressLine.get(0) should be("GROUND FLOOR FLAT")
      textAddressStructures(0).getAddressLine.get(1) should be("11 RUBY STREET")
      textAddressStructures(0).getAddressLine.get(2) should be("ADAMSDOWN")
      textAddressStructures(0).getAddressLine.get(3) should be("CARDIFF")
      textAddressStructures(0).getPostcode should be("CF24 1LP")

      textAddressStructures(1).getAddressLine.get(0) should be("ROMAIN - GROUND FLOOR FLAT")
      textAddressStructures(1).getAddressLine.get(1) should be("ROMAIN - 11 RUBY STREET")
      textAddressStructures(1).getAddressLine.get(2) should be("ROMAIN - ADAMSDOWN")
      textAddressStructures(1).getAddressLine.get(3) should be("ROMAIN - CARDIFF")
      textAddressStructures(1).getPostcode should be("CF24 1LP")

      EbarsXmlCutter.appendProposedEntriesToRemarks(reports)

      val textAddressStructuresAfter = EbarsXmlCutter.TextAddressStructures(reports)
      textAddressStructuresAfter should have size (2)

      EbarsXmlCutter.Remarks(reports) should contain("THIS IS A BLUEPRINT TEST.PLEASE DELETE/NO ACTION THIS REPORT - [PROPOSED] - [ROMAIN - GROUND FLOOR FLAT,ROMAIN - 11 RUBY STREET,ROMAIN - ADAMSDOWN,ROMAIN - CARDIFF,CF24 1LP]")
    }
  }

  "removeBS7666Address" should {
    "remove BS7666Address" in {
      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/RulesValidationEngine/CARDIFF_EDITED_CRCD_RMRKS_BOTH_PROPERTIES.xml")))

      EbarsXmlCutter.PropertyIdentities(reports).head.getContent.asScala.find (_.getName.getLocalPart == "BS7666Address") should not be(None)

      EbarsXmlCutter.removeBS7666Address(reports)

      EbarsXmlCutter.PropertyIdentities(reports).head.getContent.asScala.find (_.getName.getLocalPart == "BS7666Address") should be(None)
    }
  }

  "removePropertyGridCoords" should {
    "remove PropertyGridCoords" in {
      val reports = ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream("/xml/RulesValidationEngine/CARDIFF_EDITED_CRCD_RMRKS_BOTH_PROPERTIES.xml")))

      EbarsXmlCutter.PropertyIdentities(reports).head.getContent.asScala.find (_.getName.getLocalPart == "PropertyGridCoords") should not be(None)

      EbarsXmlCutter.removePropertyGridCoords(reports)

      EbarsXmlCutter.PropertyIdentities(reports).head.getContent.asScala.find (_.getName.getLocalPart == "PropertyGridCoords") should be(None)
    }
  }
}
