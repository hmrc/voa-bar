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

import java.time.LocalDate
import java.util.UUID

import org.scalatest.EitherValues
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import uk.gov.hmrc.voabar.models.{AddProperty, Address, CaravanRemoved, ContactDetails, Cr01Cr03Submission, RemoveProperty, ReportStatus}
import uk.gov.hmrc.voabar.models.Cr01Cr03Submission.format

class WebBarsServiceSpec extends PlaySpec with EitherValues {
  val legacyCr03Submission = {
    val address = Address("line 1 ]]>", "line2", Option("line3"), None, "BN12 4AX")
    val contactDetails = ContactDetails("John", "Doe", Option("john.doe@example.com"), Option("054252365447"))
    Cr01Cr03Submission(None, None, None,"baReport", "baRef", Option("112541"), address, contactDetails,
      true, None, LocalDate.now(), true, Some("22212"), None, Option("comment")
    )
  }

  val legacyCr03Report =
    Json.obj(
      "type" -> "Cr03Submission",
      "submission" -> format.writes(legacyCr03Submission)
    )

  val legacyCr03ReportStatus = ReportStatus(
    UUID.randomUUID().toString,
    None,
    None,
    None,
    Seq.empty,
    Seq.empty,
    None,
    None,
    None,
    None,
    Some(legacyCr03Report)
  )

  val newCr03Submission = {
    val address = Address("line 1 ]]>", "line2", Option("line3"), None, "BN12 4AX")
    val contactDetails = ContactDetails("John", "Doe", Option("john.doe@example.com"), Option("054252365447"))
    Cr01Cr03Submission(Some(AddProperty), None, None,"baReport", "baRef", Option("112541"), address, contactDetails,
      true, None, LocalDate.now(), true, Some("22212"), None, Option("comment")
    )
  }

  val newCr03Report =
    Json.obj(
      "type" -> "Cr01Cr03Submission",
      "submission" -> format.writes(newCr03Submission)
    )

  val newCr03ReportStatus = ReportStatus(
    UUID.randomUUID().toString,
    None,
    None,
    None,
    Seq.empty,
    Seq.empty,
    None,
    None,
    None,
    None,
    Some(newCr03Report)
  )

  val cr01Submission = {
    val address = Address("line 1 ]]>", "line2", Option("line3"), None, "BN12 4AX")
    val contactDetails = ContactDetails("John", "Doe", Option("john.doe@example.com"), Option("054252365447"))
    Cr01Cr03Submission(Some(RemoveProperty), Some(CaravanRemoved), None,"baReport", "baRef", Option("112541"), address, contactDetails,
      true, None, LocalDate.now(), true, Some("22212"), None, Option("comment")
    )
  }

  val cr01Report =
    Json.obj(
      "type" -> "Cr01Cr03Submission",
      "submission" -> format.writes(cr01Submission)
    )

  val cr01ReportStatus = ReportStatus(
    UUID.randomUUID().toString,
    None,
    None,
    None,
    Seq.empty,
    Seq.empty,
    None,
    None,
    None,
    None,
    Some(cr01Report)
  )


  "DefaultWebBarsService" must {
    "readReport for legacy cr03" in {
      DefaultWebBarsService.readReport(legacyCr03ReportStatus) mustBe Some(legacyCr03Submission)
    }

    "readReport for new cr03" in {
      DefaultWebBarsService.readReport(newCr03ReportStatus) mustBe Some(newCr03Submission)
    }

    "readReport for new cr01" in {
      DefaultWebBarsService.readReport(cr01ReportStatus) mustBe Some(cr01Submission)
    }
  }
}
