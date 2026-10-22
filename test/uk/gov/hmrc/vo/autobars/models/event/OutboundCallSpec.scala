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
import uk.gov.hmrc.vo.unit.test.{BaseSpec, JsonAssertions}

/**
  * @author Yuriy Tumakha
  */
class OutboundCallSpec extends BaseSpec with JsonAssertions("event"):

  "OutboundCall" should {
    "serialize to expected JSON" in {
      val event = OutboundCall("ba1445", "jobId", "billingAuthority", "transactionId", "reportNumber", "BA-trans-report.xml", "<BAreports/>")

      val eventJson = Json.toJson(event)

      event.auditType shouldBe "OutboundCall"

      compareJson(eventJson, "OutboundCall.json")
    }
  }
