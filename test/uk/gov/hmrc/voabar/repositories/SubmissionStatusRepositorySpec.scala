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

package uk.gov.hmrc.voabar.repositories

import org.mongodb.scala.SingleObservableFuture
import org.mongodb.scala.bson.collection.immutable.Document
import org.scalatest.concurrent.Eventually
import org.scalatest.time.SpanSugar
import org.scalatest.{BeforeAndAfterAll, EitherValues}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.voabar.models.{BarMongoError, Done, Error, Failed, Pending, ReportStatus, Submitted}
import uk.gov.hmrc.voabar.util.ErrorCode.{CHARACTER, INVALID_XML_XSD, TIMEOUT_ERROR, UNKNOWN_TYPE_OF_TAX}

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import scala.language.postfixOps

class SubmissionStatusRepositorySpec
  extends PlaySpec
  with BeforeAndAfterAll
  with Eventually
  with SpanSugar
  with EitherValues
  with DefaultAwaitTimeout
  with FutureAwaits
  with GuiceOneAppPerSuite
  with MockitoSugar {

  implicit class NormalizedInstant(instant: Instant) {
    def normalize: Instant = Instant.ofEpochMilli(instant.toEpochMilli)
  }

  override def fakeApplication(): Application = new GuiceApplicationBuilder()
    .configure("mongodb.uri" -> ("mongodb://localhost:27017/voa-bar" + UUID.randomUUID().toString))
    .build()

  private val mongoComponent = app.injector.instanceOf[MongoComponent]

  private val repo = app.injector.instanceOf[SubmissionStatusRepositoryImpl]

  "repository" should {

    "add error" in {
      val submissionId = "111"
      await(repo.collection.insertOne(ReportStatus(submissionId, baCode = "BA1010")).toFutureOption())

      val reportStatusError = Error(CHARACTER, Seq("message", "detail"))

      val dbResult = await(repo.addError(submissionId, reportStatusError))

      dbResult mustBe Symbol("right")

      val submission = await(repo.getByReference(submissionId))
      println(submission)
    }

    "add error without description" in {
      await(repo.collection.insertOne(ReportStatus("ggggg", baCode = "BA1010")).toFutureOption())

      val reportStatusError = Error(CHARACTER, List())

      val dbResult = await(repo.addError("ggggg", reportStatusError))

      dbResult mustBe Symbol("right")
    }

    "update status" in {
      await(repo.collection.insertOne(ReportStatus("222", baCode = "BA1010")).toFutureOption())

      val dbResult = await(repo.updateStatus("222", Submitted))

      dbResult mustBe Symbol("right")
    }

    "failed for nonExisting UUID" in {
      val dbResult = await(repo.updateStatus("nonExistingSubmissionID", Submitted))

      dbResult mustBe Symbol("left")
      dbResult mustBe Left(BarMongoError("Report status wasn't updated for nonExistingSubmissionID"))
    }

    "serialise and deserialize ReportStatus" in {

      val guid = UUID.randomUUID().toString

      val reportStatus = ReportStatus(guid, baCode = "BA2220", status = Failed.value, createdAt = Instant.now.normalize)

      await(repo.collection.insertOne(reportStatus).toFutureOption())

      val res = await(repo.getByReference(guid))

      res mustBe Symbol("right")

      res.value mustBe reportStatus
    }

    "Change status to failed for submission after timeout" in {
      val minutesToSubtract = 121

      val report = aReport().copy(createdAt = Instant.now.minus(minutesToSubtract, ChronoUnit.MINUTES))

      await(repo.saveOrUpdate(report, upsert = true))

      val reportFromDb = await(repo.getByReference(report.id))

      reportFromDb.value.status mustBe Failed.value

      reportFromDb.value.errors mustBe Seq(Error(TIMEOUT_ERROR))
    }

    "Not change status or anything else for final submission state" in {
      import org.scalatest.prop.TableDrivenPropertyChecks.*
      val finalStates =
        Table(("Final state", "errors"), (Submitted.value, Seq()), (Done.value, Seq()), (Failed.value, Seq(Error(INVALID_XML_XSD, Seq("Additional", "Parameters")))))

      val daysToSubtract = 21

      forAll(finalStates) { case (finalState: String, errors: Seq[Error]) =>
        val report = aReport().copy(createdAt = Instant.now.minus(daysToSubtract, ChronoUnit.DAYS).normalize, status = finalState, errors = errors)

        await(repo.collection.insertOne(report).toFutureOption())

        val reportFromDb = await(repo.getByReference(report.id))

        reportFromDb.value.status mustBe finalState

        reportFromDb.value.errors mustBe errors

        reportFromDb.value mustBe report
      }
    }

    "Save baCode when saving or updating submission" in {

      val submissionToStore = ReportStatus(
        UUID.randomUUID().toString,
        url = Option(s"http://localhost:2211/${UUID.randomUUID()}"),
        checksum = Option("RandomCheckSum"),
        errors = Seq(Error(UNKNOWN_TYPE_OF_TAX, Seq("Some", "Parameters"))),
        baCode = "BA2020",
        status = Submitted.value,
        filename = Option("filename.xml"),
        totalReports = Some(10)
      )
      await(repo.saveOrUpdate(submissionToStore, upsert = true))
      val submissionFromDb  = await(repo.getByReference(submissionToStore.id)).value
      submissionFromDb.baCode mustBe submissionToStore.baCode
    }

    "Not return submission older 90 days" in {

      await(repo.collection.deleteMany(Document()).toFutureOption())

      val daysToSubtract = 91

      val submissionToStore = ReportStatus(
        UUID.randomUUID().toString,
        url = Option(s"http://localhost:2211/${UUID.randomUUID()}"),
        checksum = Option("RandomCheckSum"),
        errors = Seq(Error(UNKNOWN_TYPE_OF_TAX, Seq("Some", "Parameters"))),
        baCode = "BA2020",
        status = Submitted.value,
        filename = Option("filename.xml"),
        totalReports = Some(10),
        createdAt = Instant.now.normalize
      )
      await(repo.saveOrUpdate(submissionToStore, upsert = true))
      await(repo.saveOrUpdate(
        submissionToStore.copy(id = UUID.randomUUID().toString, createdAt = Instant.now.minus(daysToSubtract, ChronoUnit.DAYS)),
        upsert = true
      ))

      val reports = await(repo.collection.countDocuments().toFutureOption())
      reports.value mustBe 2

      println("Wait for removing expired submission by Mongo background process")
      eventually(timeout(60 seconds), interval(2 seconds)) {
        await(repo.getByUser("BA2020", None)).value must have size 1
      }

      val submissionsFromDb = await(repo.getByUser("BA2020", None)).value
      submissionsFromDb must have size 1
      submissionsFromDb must contain only submissionToStore
    }
  }

  def aReport(): ReportStatus =
    ReportStatus(UUID.randomUUID().toString, baCode = "BA1010", status = Pending.value)

  override protected def afterAll(): Unit = {
    await(mongoComponent.database.drop().toFutureOption())
    mongoComponent.client.close()
  }

}
