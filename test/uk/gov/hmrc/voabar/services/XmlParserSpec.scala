/*
 * Copyright 2019 HM Revenue & Customs
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
import org.scalatest.Matchers._
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.voabar.models.BarXmlError

import scala.xml._

class XmlParserSpec extends PlaySpec with EitherValues {

  val xmlParser = new XmlParser()

  val xmlBatchSubmissionAsString = IOUtils.toString(getClass.getResource("/xml/CTValid1.xml"))
  val validWithXXE = IOUtils.toString(getClass.getResource("/xml/CTValidWithXXE.xml"))
  val invalidWithXXE2 = IOUtils.toString(getClass.getResource("/xml/WithXXE.xml"))

  val batchSubmission: Node = XML.loadString(xmlBatchSubmissionAsString)

  "Xml parser " must {
    "successfuly parse xml to DOM" in {
      val document = xmlParser.parse(xmlBatchSubmissionAsString)
      document mustBe('right)
      document.right.value.getDocumentElement().getNodeName mustBe("BAreports")
    }
    "fail for valid XML with XXS xml" in {
      val document = xmlParser.parse(validWithXXE)
      document mustBe('left)
      document.left.value mustBe(BarXmlError("DOCTYPE is disallowed when the feature \"http://apache.org/xml/features/disallow-doctype-decl\" set to true."))
    }
    "fail for xml with DTD embedded entity" in {
      val document = xmlParser.parse(validWithXXE)
      document mustBe('left)
      document.left.value mustBe(BarXmlError("DOCTYPE is disallowed when the feature \"http://apache.org/xml/features/disallow-doctype-decl\" set to true."))
    }

  }



  "A BatchSubmission" must {

    "contain a BatchHeader" in {
      (batchSubmission \ "BAreportHeader").isEmpty mustBe false
    }

    "contain a BAReports" in {
      (batchSubmission \ "BApropertyReport").isEmpty mustBe false
    }

    "contain a BatchTrailer" in {
      (batchSubmission \ "BAreportTrailer").isEmpty mustBe false
    }
  }


  "A BatchHeader" must {

    val batchHeader = batchSubmission \ "BAreportHeader"

    "contain a BillingAuthority element value" in {
      (batchHeader \ "BillingAuthority").text mustBe "VALID COUNCIL"
    }

    "contain a BillingAuthorityIdentityCode element value" in {
      (batchHeader \ "BillingAuthorityIdentityCode").text mustBe "5090"
    }

    "contain a ProcessDate element value" in {
      (batchHeader \ "ProcessDate").text mustBe "2018-01-30"
    }

    "contain an EntryDateTime element value" in {
      (batchHeader \ "EntryDateTime").text mustBe "2018-01-30T23:00:22"
    }
  }

  "A batch with one report" must {

    "contain 1 report" in {
      (batchSubmission \ "BApropertyReport").size mustBe 1
    }
  }

  "A BAPropertyReport" must {

    val propertyReport = batchSubmission \ "BApropertyReport"

    "contain a DateSent" in {
      (propertyReport.head \ "DateSent").text mustBe "2018-01-30"
    }

    "contain a TransactionIdentityBA" in {
      (propertyReport.head \ "TransactionIdentityBA").text mustBe "22121746115111"
    }

    "contain a BAidentityNumber" in {
      (propertyReport.head \ "BAidentityNumber").text mustBe "9999"
    }

    "contain a BAreportNumber" in {
      (propertyReport.head \ "BAreportNumber").text mustBe "211909"
    }

    "contain a ReasonForReportCode" in {
      (propertyReport.head \ "TypeOfTax" \\ "ReasonForReportCode").text mustBe "CR03"
    }

    "contain a ReasonForReportDescription" in {
      (propertyReport.head \ "TypeOfTax" \\ "ReasonForReportDescription").text mustBe "NEW"
    }

    "contain a IndicatedDateOfChange" in {
      (propertyReport.head \ "IndicatedDateOfChange").text mustBe "2018-05-01"
    }

    "contain a Remarks" in {
      (propertyReport.head \ "Remarks").text mustBe "THIS IS A BLUEPRINT TEST PLEASE DELETE / NO ACTION THIS REPORT"
    }

    "contain a UniquePropertyReferenceNumber" in {
      (propertyReport.head \ "ProposedEntries" \\ "UniquePropertyReferenceNumber").text mustBe "121102276285"
    }
  }

  "A BAReportTrailer" must {

    val batchTrailer = batchSubmission \ "BAreportTrailer"

    "contain a RecordCount" in {
      (batchTrailer \ "RecordCount").text mustBe "8"
    }

    "contain an EntryDateTime" in {
      (batchTrailer \ "EntryDateTime").text mustBe "2018-01-30T23:01:43"
    }

    "contain a TotalNNDRreportCount" in {
      (batchTrailer \ "TotalNNDRreportCount").text mustBe "0"
    }

    "contain a TotalCtaxReportCount" in {
      (batchTrailer \ "TotalCtaxReportCount").text mustBe "8"
    }

  }

  val multipleReportBatch = XML.loadString(IOUtils.toString(getClass.getResource("/xml/CTValid2.xml")))

  "A BAReport containing multiple BAReports" must {

    "hold the correct number of reports" in {
      (multipleReportBatch \ "BApropertyReport").size mustBe 4
    }

    "state the correct batch size in the trailer" in {
      (multipleReportBatch \ "BAreportTrailer" \ "RecordCount").text mustBe "4"
    }
  }

  "A BA batch submission" must  {

    val report:Node = XML.loadString(IOUtils.toString(getClass.getResource("/xml/CTValid2.xml")))
    val result = xmlParser.oneReportPerBatch(report)

    "be parsed into multiple smaller batches" in {

      result.size mustBe 4
    }

    "each batch should contain a single (non-empty) header node" in {
      val nonEmptyHeaders:Seq[NodeSeq] = result.map(_ \ "BAreportHeader")

      nonEmptyHeaders.size mustBe 4
      nonEmptyHeaders.forall(_.size == 1) mustBe true
    }

    "each batch should contain a single (non-empty) trailer node" in {
      val nonEmptyTrailers:Seq[NodeSeq] = result.map(_ \ "BAreportTrailer")

      nonEmptyTrailers.size mustBe 4
      nonEmptyTrailers.forall(_.size == 1) mustBe true
    }

    "each batch should contain a single (non-empty) property report" in {
      val nonEmptyPropertyReports:Seq[NodeSeq] = result.map(_ \ "BApropertyReport")

      nonEmptyPropertyReports.size shouldBe 4
      nonEmptyPropertyReports.forall(_.size == 1) mustBe true
    }
  }

}
