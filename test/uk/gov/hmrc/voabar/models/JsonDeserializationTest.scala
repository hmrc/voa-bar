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

package uk.gov.hmrc.voabar.models

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json

import java.time.format.DateTimeFormatter.ISO_DATE_TIME
import java.time.{Instant, ZoneOffset}

class JsonDeserializationTest extends PlaySpec {

  val createdAt = 1669496113080L

  private val reportDataWithoutReportErrors =
    """{ "_id" : "82ad71a8-3dbc-4d05-a035-536f4a9d89db",
      | "createdAt" : {"$date" : {"$numberLong" : "1669496113080"}},
      | "totalReports" : 1,
      | "report" : {
      |  "type" : "Cr03Submission",
      |  "submission" : {
      |   "baReport" : "BAReport1",
      |   "baRef" : "22121746115613",
      |   "uprn" : "123456",
      |   "address" : { "line1" : "55,Portland Street", "line2" : "High Steet", "line3" : "Hounslow", "line4" : "MiddleSex", "postcode" : "TW3 1TA" },
      |   "propertyContactDetails" : { "firstName" : "David", "lastName" : "Miller", "email" : "david@gmail.com", "phoneNumber" : "07250465302" },
      |   "sameContactAddress" : true,
      |   "effectiveDate" : "2012-08-02",
      |   "havePlaningReference" : false,
      |   "noPlanningReference" : "NoPlanningApplicationSubmitted",
      |   "comments" : "This Enquiry regaring property 55 portland street" } },
      |  "baCode" : "ba1445",
      |  "errors" : [ { "code" : "4500", "values" : [ ] } ],
      |  "status" : "Failed" }""".stripMargin

  "Formatters" should {
    "deserialize old version of data from database and populate missing values with defaults" in {
      val x = Json.parse(reportDataWithoutReportErrors)

      val report = x.as[ReportStatus]

      report.id must be("82ad71a8-3dbc-4d05-a035-536f4a9d89db")

      report.createdAt mustBe Instant.ofEpochMilli(createdAt)

      val createdAtZoned = report.createdAt.atZone(ZoneOffset.UTC)
      createdAtZoned.format(ISO_DATE_TIME) mustBe "2022-11-26T20:55:13.08Z"
      createdAtZoned.toString mustBe "2022-11-26T20:55:13.080Z"

      report.reportErrors mustBe empty
    }
  }

}
