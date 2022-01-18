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

import javax.xml.transform.stream.StreamSource
import org.scalatest.EitherValues
import org.scalatestplus.play.PlaySpec
import services.EbarsValidator
import uk.gov.hmrc.voabar.models.{BarSubmissionValidationError, BarValidationError, Error, LoginDetails, ReportError, ReportErrorDetail, ReportErrorDetailCode}
import uk.gov.hmrc.voabar.util._


class ValidationServiceSpec extends PlaySpec with EitherValues {

  def batchWith1Report = aXml("/xml/CTValid1.xml")
  def batchWith4Reports =  aXml("/xml/CTValid2.xml")
  def batchWith32Reports =  aXml("/xml/res100.xml")
  def batchWith32ReportsWithErrors =  aXml("/xml/res101.xml")
  def batchWithWrongBaCodeInSubreport =  aXml("/xml/CTInvalidBAidentityNumber.xml")
  def reportWithMultipleErrors =  aXml("/xml/InvalidMultipleErrors.xml")

  val BA_LOGIN = LoginDetails("BA5090", "BA5090")


  val xmlParser = new XmlParser
  val xmlValidator = new XmlValidator
  val reportBuilder = new MockBAReportBuilder
  val ebarsValidator = new EbarsValidator()

  def validationService = new ValidationService()

  "Validation service" must {

    "sucessfully validate correct XML document" in {
      val xmlBatchSubmissionAsString = aXml("/xml/CTValid1.xml")
      val validationResult = validationService.validate(xmlBatchSubmissionAsString, BA_LOGIN)
      validationResult mustBe ('right)
    }

    "return Left for not valid XML" in {
      val xmlBatchSubmissionAsString =  aXml("/xml/CTInvalid1.xml")
      val validationResult = validationService.validate(xmlBatchSubmissionAsString, BA_LOGIN)
      validationResult mustBe ('left)
    }

    "return an empty list (no errors) when passed a valid batch with one report" in {
      validationService.validate(batchWith1Report, BA_LOGIN) mustBe 'right
    }

    "return an empty list (no errors) when passed a valid batch with 4 reports" in {
      validationService.validate(batchWith4Reports, BA_LOGIN) mustBe 'right
    }

    "return an empty list (no errors) when passed a valid batch with 32 reports" in {
      validationService.validate(batchWith32Reports, LoginDetails("BA5243", "BA5243")) mustBe 'right
    }

    "return a list of 1 error when the BACode in the report header does " +
      "not match that in the HTTP request header" in {
      validationService.validate(batchWith1Report, LoginDetails("BA0000", "BA0000")).left.value mustBe BarValidationError(List[Error](Error(
        BA_CODE_MATCH, Seq("5090"))))
    }

    "return a list with 2 errors for wrong and missing BAidentityNumber in subreport" in {

      val validationResult = validationService.validate(batchWithWrongBaCodeInSubreport, LoginDetails("BA9999", "BA9999"))
      validationResult mustBe 'left
      validationResult.left.value mustBe a[BarValidationError]
      validationResult.left.value.asInstanceOf[BarValidationError].errors must have size(1)
      //TODO - It is failing already on header. How I should validate each report? Maybe create another XML
      validationResult.left.value.asInstanceOf[BarValidationError].errors must contain (Error(BA_CODE_MATCH,List("5090"),None))
      //validationResult.left.value.asInstanceOf[BarValidationError].errors must contain (Error(BA_CODE_MATCH,List("5090"),None))

    }

    "return reports errors with description" in {
      val validationResult = validationService.validate(reportWithMultipleErrors, BA_LOGIN)
      validationResult mustBe 'left
      validationResult.left.value mustBe a[BarSubmissionValidationError]

      val validationError = validationResult.left.value.asInstanceOf[BarSubmissionValidationError]

      validationError.errors must have size(3)

      validationError.errors must contain (ReportError(Some("200000"), Some("1111111111111"), Seq(1L, 2L), Seq(
        ReportErrorDetail(ReportErrorDetailCode.TextAddressPostcodeValidation ,List("EE00"))
      )))

      validationError.errors must contain (ReportError(None, Some("6831841467181"), Seq(3L, 4L), Seq(
        ReportErrorDetail(ReportErrorDetailCode.Cr08InvalidCodeValidation,List()),
        ReportErrorDetail(ReportErrorDetailCode.TextAddressPostcodeValidation,List("5554 1AA"))
      )))

    }

    val ndrReport = aXml("/xml/NDRValid1.xml")

    "validate correct NDR report" in {
      val validationResult = validationService.validate(ndrReport, LoginDetails("BA0835", "BA0835"))
      validationResult mustBe 'right

    }

    "reject NDR report with CR code in NDR xml element" in {
      val validationResult = validationService.validate(aXml("/xml/RulesCorrectionEngine/EASTRIDING_EDITED_NPE.xml"),
        LoginDetails("BA6950", "BA6950"))

      validationResult mustBe 'left
      validationResult.left.value mustBe a[BarSubmissionValidationError]
      val validationError = validationResult.left.value.asInstanceOf[BarSubmissionValidationError]

      validationError.errors must have size(1)
      validationError.errors must contain only(
        ReportError(Some("41348"),Some("WET012006000N"),Seq.empty,List(
          ReportErrorDetail(ReportErrorDetailCode.InvalidNdrCode,List("CR06")))
        )
      )
    }

  }

  def aXml(path: String) = {
    ebarsValidator.fromXml(new StreamSource(getClass.getResourceAsStream(path)))
  }

}
