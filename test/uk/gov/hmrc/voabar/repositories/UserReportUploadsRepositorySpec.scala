/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.voabar.repositories

import org.mongodb.scala.SingleObservableFuture

import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, EitherValues, OptionValues}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.mongo.MongoComponent

import java.time.Instant
import java.util.UUID


class UserReportUploadsRepositorySpec extends PlaySpec with BeforeAndAfterAll with OptionValues
  with EitherValues with DefaultAwaitTimeout with FutureAwaits  with GuiceOneAppPerSuite with MockitoSugar  {

  override def fakeApplication() = new GuiceApplicationBuilder()
    .configure("mongodb.uri" -> ("mongodb://localhost:27017/voa-bar" + UUID.randomUUID().toString))
    .build()

  lazy val mongoComponent = app.injector.instanceOf[MongoComponent]

  val repo = app.injector.instanceOf(classOf[UserReportUploadsRepository])

  "repository " should {

    "save to mongo" in {

      val id = UUID.randomUUID().toString
      val now = Instant ofEpochMilli Instant.now.toEpochMilli

      val userReportUpload = UserReportUpload(id, "BA8885", "superS3cr3dPa$$w0rd", now)

      val result = await(repo.save(userReportUpload))

      result mustBe Symbol("right")

      val resultFromDatabase = await(repo.getById(id))

      resultFromDatabase mustBe Symbol("right")

      val optionResultFromDatabase = resultFromDatabase.value

      optionResultFromDatabase mustBe defined
      optionResultFromDatabase.value mustBe userReportUpload
    }
  }

  override protected def afterAll(): Unit = {
    await(mongoComponent.database.drop().toFutureOption())
    mongoComponent.client.close()
  }

}
