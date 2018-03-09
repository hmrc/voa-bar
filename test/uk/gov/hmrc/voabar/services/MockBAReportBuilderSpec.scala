/*
 * Copyright 2018 HM Revenue & Customs
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

import org.scalatest.WordSpec
import org.scalatest.Matchers._

import scala.xml._

class MockBAReportBuilderSpec extends WordSpec{

  val builder = MockBAReportBuilder

  "A mock BA property report" should  {
    "contain the reason for report code specified" in {
      val reasonCode:String = (builder("CR03",1,0) \\ "ReasonForReportCode").text
      reasonCode shouldBe "CR03"
    }

    "contain the number of existing entries specified" in {
      val nodeseq:NodeSeq = builder("CR03",2,2) \\ "ExistingEntries"
      val existingEntriesCount:Int = nodeseq.size
      println(nodeseq)
      existingEntriesCount shouldBe (2)

    }
  }


}
