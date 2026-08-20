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

package uk.gov.hmrc.vo.autobars.repositories

import uk.gov.hmrc.vo.unit.test.db.MongoDBAppSpec

import java.time.Instant
import java.util.UUID

class UserReportUploadsRepositorySpec extends MongoDBAppSpec[UserReportUpload, DefaultUserReportUploadsRepository]:

  "UserReportUploadsRepository" should {
    "save to mongo" in {
      val id  = UUID.randomUUID.toString
      val now = Instant.ofEpochMilli(Instant.now.toEpochMilli)

      val userReportUpload = UserReportUpload(id, "BA8885", "superS3cr3dPa$$w0rd", now)

      val result = mongoRepository.save(userReportUpload).futureValue

      result shouldBe Symbol("right")

      val resultFromDatabase = mongoRepository.getById(id).futureValue

      resultFromDatabase shouldBe Symbol("right")

      val optionResultFromDatabase = resultFromDatabase.value

      optionResultFromDatabase shouldBe defined
      optionResultFromDatabase shouldBe Some(userReportUpload)
    }
  }
