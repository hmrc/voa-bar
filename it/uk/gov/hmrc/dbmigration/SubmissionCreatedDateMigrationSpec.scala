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

package uk.gov.hmrc.dbmigration

import akka.actor.ActorSystem
import org.mongodb.scala.Document
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.exists
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.time.SpanSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.lock.MongoLockRepository
import uk.gov.hmrc.voabar.dbmigration.SubmissionCreatedDateMigration
import uk.gov.hmrc.voabar.models.ReportStatus
import uk.gov.hmrc.voabar.repositories.{DefaultUserReportUploadsRepository, SubmissionStatusRepositoryImpl, UserReportUpload}

import java.time.temporal.ChronoUnit
import java.time.{Instant, ZonedDateTime}
import java.util.UUID
import scala.concurrent.ExecutionContext

/**
 * @author Yuriy Tumakha
 */
class SubmissionCreatedDateMigrationSpec extends PlaySpec with BeforeAndAfterAll
  with DefaultAwaitTimeout with FutureAwaits with Eventually with SpanSugar with GuiceOneAppPerSuite {

  override def fakeApplication(): Application = new GuiceApplicationBuilder()
    .configure("mongodb.uri" -> ("mongodb://localhost:27017/voa-bar" + UUID.randomUUID().toString))
    .build()

  implicit val ac: ExecutionContext = app.injector.instanceOf[ExecutionContext]
  implicit val actorSystem: ActorSystem = app.injector.instanceOf[ActorSystem]
  private val mongoComponent = app.injector.instanceOf[MongoComponent]
  private val submissionsRepo = app.injector.instanceOf[SubmissionStatusRepositoryImpl]
  private val userReportUploadsRepo = app.injector.instanceOf[DefaultUserReportUploadsRepository]
  private val mongoLockRepository = app.injector.instanceOf[MongoLockRepository]

  private def count(filter: Bson): Long =
    await(submissionsRepo.collection.countDocuments(filter).toFuture())

  private def save(reportStatus: ReportStatus) =
    await(submissionsRepo.collection.insertOne(reportStatus).toFuture())

  private def countReportUpload(filter: Bson): Long =
    await(userReportUploadsRepo.collection.countDocuments(filter).toFuture())

  private def saveReportUpload(userReportUpload: UserReportUpload) =
    await(userReportUploadsRepo.collection.insertOne(userReportUpload).toFuture())

  "SubmissionCreatedDateMigration" should {
    "add `createdAt` date to 4 submissions and remove 1 broken submission without `created` or `createdAt` property" in {
      save(ReportStatus("submission1", created = Some(ZonedDateTime.now), createdAt = None))
      save(ReportStatus("submission2", created = Some(ZonedDateTime.now), createdAt = None))
      save(ReportStatus("submission3", created = Some(ZonedDateTime.now), createdAt = None))
      save(ReportStatus("submission4", created = None, createdAt = Some(Instant.now)))
      save(ReportStatus("submission5", created = None, createdAt = Some(Instant.now)))
      save(ReportStatus("submission6", created = None, createdAt = None)) // simulating broken submission

      count(Document()) mustBe 6
      count(exists("created")) mustBe 3
      count(exists("createdAt")) mustBe 2

      val migrationTask = new SubmissionCreatedDateMigration(submissionsRepo, userReportUploadsRepo, mongoLockRepository)
      val updatedCount = await(migrationTask.runWithLockSubmissions())
      updatedCount mustBe 4

      println("Wait for removing expired submission by Mongo background process")
      eventually(timeout(60 seconds), interval(2 seconds)) {
        count(Document()) mustBe 5
      }
      count(exists("created")) mustBe 3
      count(exists("createdAt")) mustBe 5

      val submissions = await(submissionsRepo.collection.find(exists("created")).toFuture())
      for (submission <- submissions) {
        submission.createdAt.map(_.toEpochMilli) mustBe submission.created.map(_.toInstant.toEpochMilli)
      }
    }
  }

  "SetCreatedAtReportUploadQuery" should {
    "setCreatedAtIfEmpty" in {
      val now = Instant ofEpochMilli Instant.now.toEpochMilli
      val removeNow = now.minus(2, ChronoUnit.HOURS) // already scheduled for removing

      saveReportUpload(UserReportUpload("1", "user1", "pass", lastUpdated = Some(ZonedDateTime.now)))
      saveReportUpload(UserReportUpload("2", "user2", "pass", lastUpdated = None, createdAt = removeNow))
      saveReportUpload(UserReportUpload("3", "user3", "pass", lastUpdated = None))

      countReportUpload(Document()) mustBe 3
      countReportUpload(exists("lastUpdated")) mustBe 1
      countReportUpload(exists("createdAt")) mustBe 3

      val migrationTask = new SubmissionCreatedDateMigration(submissionsRepo, userReportUploadsRepo, mongoLockRepository)
      val updatedCount = await(migrationTask.runWithLockReportUpload())
      updatedCount mustBe 0

      println("Wait for removing expired ReportUpload by Mongo background process")
      eventually(timeout(60 seconds), interval(2 seconds)) {
        countReportUpload(Document()) mustBe 2
      }
      countReportUpload(exists("lastUpdated")) mustBe 1
      countReportUpload(exists("createdAt")) mustBe 2
    }
  }

  override protected def afterAll(): Unit = {
    await(mongoComponent.database.drop().toFutureOption())
    mongoComponent.client.close()
  }

}
