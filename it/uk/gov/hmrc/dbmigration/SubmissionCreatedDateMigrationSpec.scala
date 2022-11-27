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

package uk.gov.hmrc.dbmigration

import akka.actor.ActorSystem
import org.scalatest.BeforeAndAfterAll
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.voabar.dbmigration.SubmissionCreatedDateMigration
import uk.gov.hmrc.voabar.repositories.SubmissionStatusRepositoryImpl

import java.util.UUID
import scala.concurrent.ExecutionContext

/**
 * @author Yuriy Tumakha
 */
class SubmissionCreatedDateMigrationSpec extends PlaySpec with BeforeAndAfterAll
  with DefaultAwaitTimeout with FutureAwaits with GuiceOneAppPerSuite {

  override def fakeApplication(): Application = new GuiceApplicationBuilder()
    .configure("mongodb.uri" -> ("mongodb://localhost:27017/voa-bar" + UUID.randomUUID().toString))
    .build()

  implicit val ac: ExecutionContext = app.injector.instanceOf[ExecutionContext]
  implicit val actorSystem: ActorSystem = app.injector.instanceOf[ActorSystem]
  val mongoComponent: MongoComponent = app.injector.instanceOf[MongoComponent]
  val submissionsRepo: SubmissionStatusRepositoryImpl = app.injector.instanceOf[SubmissionStatusRepositoryImpl]

  "SubmissionCreatedDateMigration" should {
    "be completed successfully" in {
      val migrationTask = new SubmissionCreatedDateMigration(submissionsRepo)
      await(migrationTask.run())
    }
  }

  override protected def afterAll(): Unit = {
    await(mongoComponent.database.drop().toFutureOption())
    mongoComponent.client.close()
  }

}
