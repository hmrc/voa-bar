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

import org.mongodb.scala.SingleObservableFuture
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.vo.unit.test.BaseAppSpec

import java.time.Instant
import java.util.UUID

class UserReportUploadsRepositorySpec extends BaseAppSpec:

  override def fakeApplication(): Application = GuiceApplicationBuilder()
    .configure("mongodb.uri" -> ("mongodb://localhost:27017/voa-bar" + UUID.randomUUID.toString))
    .build()

  private val mongoComponent = inject[MongoComponent]
  private val repo           = inject[UserReportUploadsRepository]

  "repository " should {
    "save to mongo" in {
      val id  = UUID.randomUUID.toString
      val now = Instant.ofEpochMilli(Instant.now.toEpochMilli)

      val userReportUpload = UserReportUpload(id, "BA8885", "superS3cr3dPa$$w0rd", now)

      val result = repo.save(userReportUpload).futureValue

      result shouldBe Symbol("right")

      val resultFromDatabase = repo.getById(id).futureValue

      resultFromDatabase shouldBe Symbol("right")

      val optionResultFromDatabase = resultFromDatabase.value

      optionResultFromDatabase shouldBe defined
      optionResultFromDatabase shouldBe Some(userReportUpload)
    }
  }

  override protected def afterAll(): Unit =
    mongoComponent.database.drop().toFutureOption().futureValue
    mongoComponent.client.close()
