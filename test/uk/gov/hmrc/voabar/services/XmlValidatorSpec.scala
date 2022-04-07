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

import org.apache.commons.io.IOUtils
import org.scalatest.EitherValues
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.voabar.models.{BarXmlError, BarXmlValidationError, Error}
import uk.gov.hmrc.voabar.util.{INVALID_XML_XSD, XmlTestParser}

import java.nio.charset.StandardCharsets.UTF_8
import scala.xml.XML

class XmlValidatorSpec extends PlaySpec with EitherValues {

  val validator = new XmlValidator
  val parser = new XmlParser
  val reportBuilder = new MockBAReportBuilder
  val xmlParser = new XmlParser()

  val valid1 = xmlParser.parse(getClass.getResource("/xml/CTValid1.xml")).toOption.get
  def valid1AsStream = getClass.getResourceAsStream("/xml/CTValid1.xml")
  val valid2 = xmlParser.parse(getClass.getResource("/xml/CTValid2.xml")).toOption.get
  val invalid1 = xmlParser.parse(getClass.getResource("/xml/CTInvalid1.xml")).toOption.get
  val invalid2 = xmlParser.parse(getClass.getResource("/xml/CTInvalid2.xml")).toOption.get
  def withXXE = getClass.getResourceAsStream("/xml/WithXXE.xml")
  def wellFormatted = getClass.getResourceAsStream("/xml/WellFormatted.xml")
  def notWellFormatted = getClass.getResourceAsStream("/xml/NotWellFormatted.xml")


  "A valid ba batch submission xml file (valid1)" must {
    "validate successfully" in {
      validator.validate(valid1) mustBe Symbol("right")
    }
  }

  "An invalid ba batch submission xml file (invalid1)" must {
    "not validate successfully" in {
      validator.validate(invalid1) mustBe Symbol("left")
      //errors.size mustBe 4
    }
  }

  "A valid ba batch submission xml file (valid2)" must {
    "validate successfully" in {
      validator.validate(valid2) mustBe Symbol("right")
    }
  }

  "An invalid ba batch submission xml file (invalid2)" must {
    "not validate successfully and contain a CouncilTaxBand related error" in {
      validator.validate(invalid2) mustBe Symbol("left")
      //errors.size mustBe 18
      //assert(errors.toString.contains("CouncilTaxBand"))
    }
  }

  "A invalid XML " must {
    "fail for wrong namespace" in {
      val invalidNamespaceDocument = IOUtils.toString(getClass.getResource("/xml/CTInvalid1.xml"), UTF_8)
        .replaceAll("http://www.govtalk.gov.uk/LG/Valuebill", "uri:wrong")

      val doc = XmlTestParser.parseXml(invalidNamespaceDocument)

      val result = validator.validate(doc)

      result mustBe Symbol("left")

      result.left.value mustBe BarXmlValidationError(List(Error(INVALID_XML_XSD, List("Error on line -1: Cannot find the declaration of element 'BAreports'."))))

    }

    "fail for misspelled root element" in {

      val invalidNamespaceDocument = IOUtils.toString(getClass.getResource("/xml/CTInvalid1.xml"), UTF_8)
        .replaceAll("BAreports", "bareports")

      val doc = XmlTestParser.parseXml(invalidNamespaceDocument)

      val result = validator.validate(doc)

      result mustBe Symbol("left")

      result.left.value mustBe BarXmlValidationError(List(Error(INVALID_XML_XSD, List("Error on line -1: Cannot find the declaration of element 'bareports'."))))

    }

  }

  val batchWith4Reports = IOUtils.toString(getClass.getResource("/xml/CTValid2.xml"), UTF_8)

  "Xml validator" should {

    "reject not well formatted XML" in {
      val result = validator.validateInputXmlForXEE(notWellFormatted)
      result.left.value mustBe a[BarXmlError]
      result mustBe Symbol("left")
    }

    "reject xml with XXE" in {
      val result = validator.validateInputXmlForXEE(withXXE)
      result mustBe Symbol("left")
      result.left.value mustBe BarXmlError("""XML read error, invalid XML document, DOCTYPE is disallowed when the feature "http://apache.org/xml/features/disallow-doctype-decl" set to true.""")
    }

    "validate well formatted xml" in {
      validator.validateInputXmlForXEE(valid1AsStream) mustBe Symbol("right")
    }

    "validate well formated xml even when it doesn't follow VOA-BAR xml schema" in {
      validator.validateInputXmlForXEE(wellFormatted) mustBe Symbol("right")
    }


  }


//TODO - we are getting only one error. Should we validate with original file?
  "another test" should {
    val batchWith32ReportsWithErrors = IOUtils.toString(getClass.getResource("/xml/res101.xml"), UTF_8)

    "return a list of errors when a batch containing 32 reports has multiple errors" in {
      val invalidBatch = XML.loadString(batchWith32ReportsWithErrors).toString()

      val validationResutl = validator.validate(XmlTestParser.parseXml(invalidBatch))

      validationResutl mustBe Symbol("left")

      validationResutl.left.value mustBe a[BarXmlValidationError]
      validationResutl.left.value.asInstanceOf[BarXmlValidationError].errors must contain only (
        Error(INVALID_XML_XSD,List("Error on line -1: The value '0£' of element 'TotalNNDRreportCount' is not valid."))
        )
    }

  }

}