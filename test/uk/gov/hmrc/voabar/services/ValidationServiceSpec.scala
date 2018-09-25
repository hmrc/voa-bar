/*
 * Copyright 2018 HM Revenue & Customs
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

import org.apache.commons.io.IOUtils
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.voabar.models.Error
import uk.gov.hmrc.voabar.util._

import scala.xml.{Node, XML}

class ValidationServiceSpec extends PlaySpec {

  val batchWith1Report = IOUtils.toString(getClass.getResource("/xml/CTValid1.xml"))
  val batchWith4Reports = IOUtils.toString(getClass.getResource("/xml/CTValid2.xml"))
  val batchWith32Reports = IOUtils.toString(getClass.getResource("/xml/res100.xml"))
  val batchWith32ReportsWithErrors = IOUtils.toString(getClass.getResource("/xml/res101.xml"))


  val xmlParser = new XmlParser
  val xmlValidator = new XmlValidator
  val charValidator = new CharacterValidator
  val reportBuilder = new MockBAReportBuilder
  val businessRules= new BusinessRules()
  def validationService(baCode:String): ValidationService = new ValidationService(
    xmlValidator, xmlParser, charValidator, businessRules)

  "Validation service" must {

    "sucessfully validate correct XML document" in {
      val xmlBatchSubmissionAsString = IOUtils.toString(getClass.getResource("/xml/CTValid1.xml"))
      val validationResult = validationService("9999").validate(xmlBatchSubmissionAsString)
      validationResult mustBe ('right)
    }

    "return Left for not valid XML" in {
      val xmlBatchSubmissionAsString = IOUtils.toString(getClass.getResource("/xml/CTInvalid1.xml"))
      val validationResult = validationService("9999").validate(xmlBatchSubmissionAsString)
      validationResult mustBe ('left)
    }

    "return an empty list (no errors) when passed a valid batch with one report" in {
        val validBatch:Node = XML.loadString(batchWith1Report)
        validationService("9999").xmlNodeValidation(validBatch).isEmpty mustBe true
    }

    "return an empty list (no errors) when passed a valid batch with 4 reports" in {
      val validBatch:Node = XML.loadString(batchWith4Reports)
      validationService("9999").xmlNodeValidation(validBatch).isEmpty mustBe true
    }

    "return an empty list (no errors) when passed a valid batch with 32 reports" in {
      val validBatch:Node = XML.loadString(batchWith32Reports)
      validationService("5243").xmlNodeValidation(validBatch).isEmpty mustBe true
    }

    "return a list of 1 error when the BACode in the report header does " +
      "not match that in the HTTP request header" ignore {
      val validBatch = XML.loadString(batchWith1Report)
      validationService("0000").xmlNodeValidation(validBatch) mustBe List[Error](Error(
        BA_CODE_MATCH, Seq()))
    }


    "return a list of 2 errors when the BACode in the report header does " +
      "not match that in the HTTP request header and the report header contains 1 illegal element" ignore {
      val validBatch = XML.loadString(batchWith1Report)
      val invalidBatch = reportBuilder.invalidateBatch(validBatch.head, Map("BillingAuthority" -> "BadElement"))
      validationService("0000").xmlNodeValidation(invalidBatch.head) mustBe List[Error](
        Error(BA_CODE_MATCH, Seq()),
        Error(INVALID_XML_XSD, Seq("Invalid content was found starting with element 'BadElement'. " +
          "One of '{\"http://www.govtalk.gov.uk/LG/Valuebill\":BillingAuthority}' is expected."))
      )
    }

    "return a list of 3 errors when the BACode in the report batch header of a batch of 4 reports does " +
      "not match that in the HTTP request header, the report header contains 1 illegal element and each " +
      "of the 4 reports contains 1 illegal element " ignore {
      val validBatch = XML.loadString(batchWith4Reports)
      val invalidBatch = reportBuilder.invalidateBatch(validBatch.head, Map(
        "BAreportNumber" -> "WrongElement", "BillingAuthority" -> "IllegalElement",
        "PropertyDescriptionText" -> "BadElement"))

       validationService("0000").xmlNodeValidation(invalidBatch.head) mustBe List[Error](
         Error(BA_CODE_MATCH, Seq()),
         Error(INVALID_XML_XSD, Seq("Invalid content was found starting with element 'IllegalElement'. " +
           "One of '{\"http://www.govtalk.gov.uk/LG/Valuebill\":BillingAuthority}' is expected.")),
         Error(INVALID_XML_XSD, Seq("Invalid content was found starting with element 'WrongElement'. " +
           "One of '{\"http://www.govtalk.gov.uk/LG/Valuebill\":BAreportNumber}' is expected.")),
         Error(INVALID_XML_XSD,Seq("Invalid content was found starting with element 'BadElement'. " +
           "One of '{\"http://www.govtalk.gov.uk/LG/Valuebill\":PrimaryDescriptionCode, \"http://www.govtalk." +
           "gov.uk/LG/Valuebill\":SecondaryDescriptionCode, \"http://www.govtalk.gov.uk/LG/Valuebill\":PropertyDescriptionText}' is expected.")))

    }

    "return a list of 3 errors when the BACode in the report batch header of a batch of 4 reports does " +
      "not match that in the HTTP request header, the report header contains 1 illegal element, " +
      "the BillingAuthority name contains illegal chars and each of the 4 reports contains 1 illegal element " ignore {
      val validBatch = XML.loadString(batchWith4Reports)
      val invalidBatch = reportBuilder.invalidateBatch(validBatch.head, Map(
        "BAreportNumber" -> "WrongElement", "BillingAuthority" -> "IllegalElement",
        "PropertyDescriptionText" -> "BadElement", "SOME VALID COUNCIL" -> "Some Valid Council"))

      validationService("0000").xmlNodeValidation(invalidBatch.head) mustBe List[Error](
        Error(BA_CODE_MATCH, Seq()),
        Error(INVALID_XML_XSD, Seq("Invalid content was found starting with element 'IllegalElement'. " +
          "One of '{\"http://www.govtalk.gov.uk/LG/Valuebill\":BillingAuthority}' is expected.")),
        Error(INVALID_XML_XSD, Seq("Invalid content was found starting with element 'WrongElement'. " +
          "One of '{\"http://www.govtalk.gov.uk/LG/Valuebill\":BAreportNumber}' is expected.")),
        Error(INVALID_XML_XSD,Seq("Invalid content was found starting with element 'BadElement'. " +
          "One of '{\"http://www.govtalk.gov.uk/LG/Valuebill\":PrimaryDescriptionCode, \"http://www.govtalk." +
          "gov.uk/LG/Valuebill\":SecondaryDescriptionCode, \"http://www.govtalk.gov.uk/LG/Valuebill\":PropertyDescriptionText}' is expected.")),
        Error(CHARACTER,Seq("Header","IllegalElement","Some Valid Council"))
      )
    }

    "return a list of 1 error when a batch containing 1 report has an illegal char within the property report" in {
      val validBatch = XML.loadString(batchWith1Report)
      val invalidBatch = reportBuilder.invalidateBatch(validBatch.head, Map(
        "NAME" -> "name"
      ))

      validationService("9999").xmlNodeValidation(invalidBatch.head) mustBe List[Error](
        Error(CHARACTER,Seq("211909","PersonGivenName","name"))
      )
    }

    "return a list of errors when a batch containing 32 reports has multiple errors" ignore {
      val invalidBatch = XML.loadString(batchWith32ReportsWithErrors)
      validationService("8888").xmlNodeValidation(invalidBatch.head) mustBe List(
        Error(BA_CODE_MATCH,List()),
        Error(INVALID_XML_XSD,List("Invalid content was found starting with element 'IllegalElement'. One of " +
          "'{\"http://www.govtalk.gov.uk/LG/Valuebill\":EntryDateTime}' is expected.")),
        Error(INVALID_XML_XSD,List("'0£' is not a valid value for 'integer'.")),
        Error(INVALID_XML_XSD,List("The value '0£' of element 'TotalNNDRreportCount' is not valid.")),
        Error(CHARACTER ,List("Trailer", "IllegalElement", "Some text")),
        Error(CHARACTER,List("Trailer", "TotalNNDRreportCount", "0£")),
        Error(CHARACTER,List("138161", "StreetDescription", "23 NeW ST")),
        Error(INVALID_XML_XSD,List("Invalid content was found starting with element 'BadElement'. " +
          "One of '{\"http://www.govtalk.gov.uk/LG/Valuebill\":TypeOfTax}' is expected.")),
        Error(CHARACTER,List("138156", "Town", "WIMBLETOWN£")),
        Error(INVALID_XML_XSD,List("Invalid content was found starting with element 'ExistingEntries'. " +
          "One of '{\"http://www.govtalk.gov.uk/LG/Valuebill\":ProposedEntries, \"http://www.govtalk.gov.uk/LG/Value" +
          "bill\":IndicatedDateOfChange}' is expected.")),
        Error(ONE_EXISTING,List("138159", "CR09")))
    }
  }

}
