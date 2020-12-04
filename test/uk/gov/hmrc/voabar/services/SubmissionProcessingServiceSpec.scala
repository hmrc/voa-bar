/*
 * Copyright 2020 HM Revenue & Customs
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

import java.util.UUID

import org.apache.commons.io.IOUtils
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatestplus.play.PlaySpec
import play.api.inject.guice.GuiceInjectorBuilder

class SubmissionProcessingServiceSpec extends PlaySpec with TableDrivenPropertyChecks {

  val batchWith1Report = IOUtils.toByteArray(getClass.getResourceAsStream("/xml/CTValid1.xml"))
  val batchWithMoreReports = IOUtils.toByteArray(getClass.getResourceAsStream("/xml/CTValid2.xml"))
  val wrongHeaderTrailer = IOUtils.toByteArray(getClass.getResourceAsStream("/xml/wrong-header-trailer.xml"))

  val injector = new GuiceInjectorBuilder()
    .configure("key" -> "value")
    .injector()

  def submissionProcessingService = injector.instanceOf[SubmissionProcessingService]


  "SubmissionProcessingService" should {
    "Process and fix already valid XML" ignore {
      submissionProcessingService.processAsV1(batchWith1Report, "BA5090", UUID.randomUUID().toString.toLowerCase) mustBe(true)
    }

    "Process2 and fix already valid XML" ignore {
      submissionProcessingService.processAsV1(batchWithMoreReports, "BA5090", UUID.randomUUID().toString.toLowerCase) mustBe(true)
    }

    "Process and fix XML with wrong header and trailer" ignore {
      submissionProcessingService.processAsV1(wrongHeaderTrailer, "BA5090", UUID.randomUUID().toString.toLowerCase()) mustBe(true)
    }

    "Correct all invalid XML and validate them successfully as V2 " ignore {

      val xml = Table(("Invalid XML to be fixed","Ba Login"),
      ("/xml/RulesCorrectionEngine/CR05_BOTH_PROPERTIES.xml", "BA6950"),
      ("/xml/RulesCorrectionEngine/CR12_BOTH_PROPERTIES.xml", "BA6950"),
      ("/xml/RulesCorrectionEngine/CR14_BOTH_PROPERTIES.xml", "BA6950"),
      ("/xml/BEXLEY_UNEDITED_INVALID_TAX_BAND.xml", "BA5120")
      )

      forAll(xml) { case  (xmlFile, baLogin) =>
        val brokenXml = IOUtils.toByteArray(getClass.getResourceAsStream(xmlFile))
        submissionProcessingService.processAsV1(brokenXml, baLogin, UUID.randomUUID().toString.toLowerCase) mustBe(true)
      }
    }
  }


}
