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

package uk.gov.hmrc.voabar.models

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json

class JsonDeserializationTest extends PlaySpec {

  val reportDataWithoutReportErrors =
    """{ "_id" : "82ad71a8-3dbc-4d05-a035-536f4a9d89db",
      | "created" : "2020-07-23T05:01:40.064+01:00[Europe/London]",
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

      report.reportErrors mustBe empty

    }
  }


}
