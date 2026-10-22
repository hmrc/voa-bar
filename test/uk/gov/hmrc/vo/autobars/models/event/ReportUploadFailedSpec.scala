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

package uk.gov.hmrc.vo.autobars.models.event

import play.api.libs.json.Json
import uk.gov.hmrc.vo.autobars.models.*
import uk.gov.hmrc.vo.autobars.util.ErrorCode.INVALID_XML_XSD
import uk.gov.hmrc.vo.unit.test.{BaseSpec, JsonAssertions}

/**
  * @author Yuriy Tumakha
  */
class ReportUploadFailedSpec extends BaseSpec with JsonAssertions("event"):

  "ReportUploadFailed with BarXmlError" should {
    "serialize to expected JSON" in {
      val event     = ReportUploadFailed("ba1445", BarXmlError("Error message"))
      val eventJson = Json.toJson(event)

      event.auditType shouldBe "ReportUploadFailed"

      compareJson(eventJson, "ReportUploadFailed_BarXmlError.json")
    }
  }

  "ReportUploadFailed with BarXmlValidationError" should {
    "serialize to expected JSON" in {
      val xmlError  = BarXmlValidationError(List(
        Error(INVALID_XML_XSD, List("Error on line -1: Cannot find the declaration of element 'BAreports'."), Some("submission detail"))
      ))
      val event     = ReportUploadFailed("ba1445", xmlError)
      val eventJson = Json.toJson(event)

      event.auditType shouldBe "ReportUploadFailed"

      compareJson(eventJson, "ReportUploadFailed_BarXmlValidationError.json")
    }
  }

  "ReportUploadFailed with BarValidationError" should {
    "serialize to expected JSON" in {
      val xmlError  = BarValidationError(List(
        Error(INVALID_XML_XSD, List("Error on line -1: Cannot find the declaration of element 'BAreports'."), Some("submission detail"))
      ))
      val event     = ReportUploadFailed("ba1445", xmlError)
      val eventJson = Json.toJson(event)

      event.auditType shouldBe "ReportUploadFailed"

      compareJson(eventJson, "ReportUploadFailed_BarXmlValidationError.json")
    }
  }

  "ReportUploadFailed with BarSubmissionValidationError" should {
    "serialize to expected JSON" in {
      val submissionError = BarSubmissionValidationError(List(
        ReportError(
          Some("reportNumber"),
          Some("baTransaction"),
          Seq(1L, 2L, 3L),
          List(ReportErrorDetail(ReportErrorDetailCode.InvalidNdrCode, List("CR06")))
        )
      ))
      val event           = ReportUploadFailed("ba1445", submissionError)
      val eventJson       = Json.toJson(event)

      event.auditType shouldBe "ReportUploadFailed"

      compareJson(eventJson, "ReportUploadFailed_BarSubmissionValidationError.json")
    }
  }

  "ReportUploadFailed with BarMongoError" should {
    "serialize to expected JSON" in {
      val event     = ReportUploadFailed("ba1445", BarMongoError("MongoDB error"))
      val eventJson = Json.toJson(event)

      event.auditType shouldBe "ReportUploadFailed"

      compareJson(eventJson, "ReportUploadFailed_BarMongoError.json")
    }
  }

  "ReportUploadFailed with BarEbarError" should {
    "serialize to expected JSON" in {
      val event     = ReportUploadFailed("ba0114", BarEbarError("Exception message"))
      val eventJson = Json.toJson(event)

      event.auditType shouldBe "ReportUploadFailed"

      compareJson(eventJson, "ReportUploadFailed_BarEbarError.json")
    }
  }

  "ReportUploadFailed with BarEmailError" should {
    "serialize to expected JSON" in {
      val event     = ReportUploadFailed("ba0114", BarEmailError("Exception message"))
      val eventJson = Json.toJson(event)

      event.auditType shouldBe "ReportUploadFailed"

      compareJson(eventJson, "ReportUploadFailed_BarEbarError.json")
    }
  }

  "ReportUploadFailed with BarUnknownError" should {
    "serialize to expected JSON" in {
      val event     = ReportUploadFailed("ba0114", BarUnknownError("Unable to process upload"))
      val eventJson = Json.toJson(event)

      event.auditType shouldBe "ReportUploadFailed"

      compareJson(eventJson, "ReportUploadFailed_BarUnknownError.json")
    }
  }
