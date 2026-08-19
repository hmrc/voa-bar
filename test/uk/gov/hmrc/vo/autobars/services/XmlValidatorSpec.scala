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

import org.apache.commons.io.IOUtils
import uk.gov.hmrc.vo.autobars.models.{BarXmlError, BarXmlValidationError, Error}
import uk.gov.hmrc.vo.autobars.util.XmlTestParser
import uk.gov.hmrc.vo.autobars.util.ErrorCode.INVALID_XML_XSD
import uk.gov.hmrc.vo.unit.test.BaseSpec

import java.nio.charset.StandardCharsets.UTF_8
import scala.xml.XML

class XmlValidatorSpec extends BaseSpec:

  private val validator = XmlValidator()
  private val xmlParser = XmlParser()

  private val valid1           = xmlParser.parse(getClass.getResource("/xml/CTValid1.xml")).fold(err => throw Exception(err.toString), identity)
  private val valid1AsStream   = getClass.getResourceAsStream("/xml/CTValid1.xml")
  private val valid2           = xmlParser.parse(getClass.getResource("/xml/CTValid2.xml")).fold(err => throw Exception(err.toString), identity)
  private val invalid1         = xmlParser.parse(getClass.getResource("/xml/CTInvalid1.xml")).fold(err => throw Exception(err.toString), identity)
  private val invalid2         = xmlParser.parse(getClass.getResource("/xml/CTInvalid2.xml")).fold(err => throw Exception(err.toString), identity)
  private val withXXE          = getClass.getResourceAsStream("/xml/WithXXE.xml")
  private val wellFormatted    = getClass.getResourceAsStream("/xml/WellFormatted.xml")
  private val notWellFormatted = getClass.getResourceAsStream("/xml/NotWellFormatted.xml")

  "A valid ba batch submission xml file (valid1)" should {
    "validate successfully" in {
      validator.validate(valid1) shouldBe Symbol("right")
    }
  }

  "An invalid ba batch submission xml file (invalid1)" should {
    "not validate successfully" in {
      validator.validate(invalid1) shouldBe Symbol("left")
    }
  }

  "A valid ba batch submission xml file (valid2)" should {
    "validate successfully" in {
      validator.validate(valid2) shouldBe Symbol("right")
    }
  }

  "An invalid ba batch submission xml file (invalid2)" should {
    "not validate successfully and contain a CouncilTaxBand related error" in {
      validator.validate(invalid2) shouldBe Symbol("left")
    }
  }

  "A invalid XML " should {
    "fail for wrong namespace" in {
      val invalidNamespaceDocument = IOUtils.toString(getClass.getResource("/xml/CTInvalid1.xml"), UTF_8)
        .replaceAll("http://www.govtalk.gov.uk/LG/Valuebill", "uri:wrong")

      val doc = XmlTestParser.parseXml(invalidNamespaceDocument)

      val result = validator.validate(doc)

      result shouldBe Symbol("left")

      result.left.value shouldBe BarXmlValidationError(List(Error(INVALID_XML_XSD, List("Error on line -1: Cannot find the declaration of element 'BAreports'."))))
    }

    "fail for misspelled root element" in {
      val invalidNamespaceDocument = IOUtils.toString(getClass.getResource("/xml/CTInvalid1.xml"), UTF_8)
        .replaceAll("BAreports", "bareports")

      val doc = XmlTestParser.parseXml(invalidNamespaceDocument)

      val result = validator.validate(doc)

      result shouldBe Symbol("left")

      result.left.value shouldBe BarXmlValidationError(List(Error(INVALID_XML_XSD, List("Error on line -1: Cannot find the declaration of element 'bareports'."))))
    }
  }

  "XmlValidator" should {
    "reject not well formatted XML" in {
      val result = validator.validateInputXmlForXEE(notWellFormatted)
      result.left.value shouldBe a[BarXmlError]
      result            shouldBe Symbol("left")
    }

    "reject xml with XXE" in {
      val result = validator.validateInputXmlForXEE(withXXE)
      result            shouldBe Symbol("left")
      result.left.value shouldBe BarXmlError(
        """XML read error, invalid XML document, DOCTYPE is disallowed when the feature "http://apache.org/xml/features/disallow-doctype-decl" set to true."""
      )
    }

    "validate well formatted xml" in {
      validator.validateInputXmlForXEE(valid1AsStream) shouldBe Symbol("right")
    }

    "validate well formated xml even when it doesn't follow BARS xml schema" in {
      validator.validateInputXmlForXEE(wellFormatted) shouldBe Symbol("right")
    }
  }

  "Batch with 32 reports" should {
    "return a list of errors when a batch containing 32 reports has multiple errors" in {
      val batchWith32ReportsWithErrors = IOUtils.toString(getClass.getResource("/xml/res101.xml"), UTF_8)

      val invalidBatch = XML.loadString(batchWith32ReportsWithErrors).toString()

      val validationResult = validator.validate(XmlTestParser.parseXml(invalidBatch))

      validationResult shouldBe Symbol("left")

      validationResult.left.value                                          shouldBe a[BarXmlValidationError]
      validationResult.left.value.asInstanceOf[BarXmlValidationError].errors should contain only
        Error(INVALID_XML_XSD, List("Error on line -1: The value '0£' of element 'TotalNNDRreportCount' is not valid."))
    }
  }
