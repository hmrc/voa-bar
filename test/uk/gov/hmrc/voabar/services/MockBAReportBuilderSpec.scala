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

import java.nio.charset.StandardCharsets.UTF_8
import org.apache.commons.io.IOUtils
import org.scalatestplus.play.PlaySpec

import scala.xml._

class MockBAReportBuilderSpec extends PlaySpec {

  val reportBuilder = new MockBAReportBuilder

  "A mock BA property report" must {

    "contain the reason for report code specified" in {
      val reasonCode: String = (reportBuilder("CR03", 1000, 1, 0) \\ "ReasonForReportCode").text
      reasonCode mustBe "CR03"
    }

    "contain the corresponding reason for report description for a given reason code" in {
      val reasonDescription: String = (reportBuilder("CR05", 1000, 1, 1) \\ "ReasonForReportDescription").text
      reasonDescription mustBe "Reconstituted Property"
    }

    "contain the BA code specified" in {
      val baCode: String = (reportBuilder("CR03", 1000, 1, 0) \\ "BAidentityNumber").text
      baCode mustBe "1000"
    }

    "contain the number of existing entries and proposed entries specified" in {
      val baPropertyReport: NodeSeq = reportBuilder("CR03", 1000, 3, 0)
      val existingEntries           = baPropertyReport \\ "ExistingEntries"
      val proposedEntries           = baPropertyReport \\ "ProposedEntries"
      existingEntries.size mustBe 3
      proposedEntries.size mustBe 0
    }

    "the mock BA report builder" must {

      val batchSubmission = XML.loadString(IOUtils.toString(getClass.getResource("/xml/CTValid2.xml"), UTF_8))

      "modify a given report by replacing an existing element label with a new label" in {
        val result = reportBuilder.invalidateBatch(batchSubmission, Map("BAreportHeader" -> "invalidHeader"))
        (result \\ "BAreportHeader").size mustBe 0
        (result \\ "invalidHeader").size mustBe 1
      }

      "modify a given report by replacing existing data with some new data" in {
        val result = reportBuilder.invalidateBatch(batchSubmission, Map("SOME VALID COUNCIL" -> "INVALID COUNCIL"))
        (result \\ "BillingAuthority").text mustBe "INVALID COUNCIL"
      }

      "modify a given report in multiple ways at once" in {
        val result =
          reportBuilder.invalidateBatch(batchSubmission, Map("SOME VALID COUNCIL" -> "INVALID COUNCIL", "5090" -> "XXXX", "RecordCount" -> "InvalidElement"))
        (result \\ "BillingAuthority").text mustBe "INVALID COUNCIL"
        (result \\ "BillingAuthorityIdentityCode").text mustBe "XXXX"
        (result \\ "InvalidElement").size mustBe 1

      }
    }
  }
}
