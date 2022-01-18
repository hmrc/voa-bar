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

package uk.gov.hmrc.repositories

import java.time.ZonedDateTime
import java.util.UUID

import org.scalatest.{BeforeAndAfterAll, EitherValues, OptionValues}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import org.scalatestplus.mockito.MockitoSugar
import play.api.inject.guice.GuiceApplicationBuilder
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.voabar.repositories.{UserReportUpload, UserReportUploadsRepository}

import scala.concurrent.ExecutionContext.Implicits.global


class UserReportUploadsRepositorySpecextends extends PlaySpec with BeforeAndAfterAll with OptionValues
  with EitherValues with DefaultAwaitTimeout with FutureAwaits  with GuiceOneAppPerSuite with MockitoSugar  {


  override def fakeApplication() = new GuiceApplicationBuilder()
    .configure("mongodb.uri" -> ("mongodb://localhost:27017/voa-bar" + UUID.randomUUID().toString))
    .build()

  lazy val mongoComponent = app.injector.instanceOf(classOf[ReactiveMongoComponent])

  val repo = app.injector.instanceOf(classOf[UserReportUploadsRepository])

  "repository " should {

    "save to mongo" in {

      val id = UUID.randomUUID().toString


      val userReportUpload = UserReportUpload(id, "BA8885", "superS3cr3dPa$$w0rd", ZonedDateTime.now())

      val result = await(repo.save(userReportUpload))

      result mustBe('right)

      val resultFromDatabase = await(repo.getById(id))

      resultFromDatabase mustBe('right)

      val optionResultFromDatabase = resultFromDatabase.right.value

      optionResultFromDatabase mustBe defined
      optionResultFromDatabase.value mustBe userReportUpload

    }
  }

  override protected def afterAll(): Unit = {
    await(mongoComponent.mongoConnector.db().drop())
    mongoComponent.mongoConnector.close()
  }
}
