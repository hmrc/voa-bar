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
import org.mongodb.scala.bson.collection.immutable.Document
import org.scalatest.concurrent.Eventually
import org.scalatest.time.SpanSugar
import uk.gov.hmrc.vo.autobars.models.{BarMongoError, Done, Error, Failed, Pending, ReportStatus, Submitted}
import uk.gov.hmrc.vo.autobars.util.ErrorCode.{CHARACTER, INVALID_XML_XSD, TIMEOUT_ERROR, UNKNOWN_TYPE_OF_TAX}
import uk.gov.hmrc.vo.unit.test.db.MongoDBAppSpec

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import scala.language.postfixOps

class SubmissionStatusRepositorySpec extends MongoDBAppSpec[ReportStatus, SubmissionStatusRepositoryImpl] with Eventually with SpanSugar:

  implicit class NormalizedInstant(instant: Instant):
    def normalize: Instant = Instant.ofEpochMilli(instant.toEpochMilli)

  private def aReport(): ReportStatus =
    ReportStatus(UUID.randomUUID.toString, baCode = "BA1010", status = Pending.value)

  "SubmissionStatusRepository" should {
    "add error" in {
      val submissionId = "111"
      mongoRepository.collection.insertOne(ReportStatus(submissionId, baCode = "BA1010")).toFutureOption().futureValue

      val reportStatusError = Error(CHARACTER, Seq("message", "detail"))
      val dbResult          = mongoRepository.addError(submissionId, reportStatusError).futureValue

      dbResult shouldBe Symbol("right")

      val submission = mongoRepository.getByReference(submissionId).futureValue
      println(submission)
    }

    "add error without description" in {
      mongoRepository.collection.insertOne(ReportStatus("ggggg", baCode = "BA1010")).toFutureOption().futureValue

      val reportStatusError = Error(CHARACTER, List())
      val dbResult          = mongoRepository.addError("ggggg", reportStatusError).futureValue

      dbResult shouldBe Symbol("right")
    }

    "update status" in {
      mongoRepository.collection.insertOne(ReportStatus("222", baCode = "BA1010")).toFutureOption().futureValue

      val dbResult = mongoRepository.updateStatus("222", Submitted).futureValue

      dbResult shouldBe Symbol("right")
    }

    "failed for nonExisting UUID" in {
      val dbResult = mongoRepository.updateStatus("nonExistingSubmissionID", Submitted).futureValue

      dbResult shouldBe Symbol("left")
      dbResult shouldBe Left(BarMongoError("Report status wasn't updated for nonExistingSubmissionID"))
    }

    "serialise and deserialize ReportStatus" in {
      val guid         = UUID.randomUUID.toString
      val reportStatus = ReportStatus(guid, baCode = "BA2220", status = Failed.value, createdAt = Instant.now.normalize)

      mongoRepository.collection.insertOne(reportStatus).toFutureOption().futureValue

      val res = mongoRepository.getByReference(guid).futureValue

      res       shouldBe Symbol("right")
      res.value shouldBe reportStatus
    }

    "change status to failed for submission after timeout" in {
      val minutesToSubtract = 121

      val report = aReport().copy(createdAt = Instant.now.minus(minutesToSubtract, ChronoUnit.MINUTES))

      mongoRepository.saveOrUpdate(report, upsert = true).futureValue

      val reportFromDb = mongoRepository.getByReference(report.id).futureValue

      reportFromDb.value.status shouldBe Failed.value
      reportFromDb.value.errors shouldBe Seq(Error(TIMEOUT_ERROR))
    }

    "not change status or anything else for final submission state" in {
      val finalStates = Table(
        ("Final state", "errors"),
        (Submitted.value, Seq()),
        (Done.value, Seq()),
        (Failed.value, Seq(Error(INVALID_XML_XSD, Seq("Additional", "Parameters"))))
      )

      val daysToSubtract = 21

      forAll(finalStates) { case (finalState: String, errors: Seq[Error]) =>
        val report = aReport().copy(createdAt = Instant.now.minus(daysToSubtract, ChronoUnit.DAYS).normalize, status = finalState, errors = errors)

        mongoRepository.collection.insertOne(report).toFutureOption().futureValue

        val reportFromDb = mongoRepository.getByReference(report.id).futureValue

        reportFromDb.value.status shouldBe finalState
        reportFromDb.value.errors shouldBe errors
        reportFromDb.value        shouldBe report
      }
    }

    "save baCode when saving or updating submission" in {
      val submissionToStore = ReportStatus(
        UUID.randomUUID.toString,
        url = Option(s"http://localhost:2211/${UUID.randomUUID}"),
        checksum = Option("RandomCheckSum"),
        errors = Seq(Error(UNKNOWN_TYPE_OF_TAX, Seq("Some", "Parameters"))),
        baCode = "BA2020",
        status = Submitted.value,
        filename = Option("filename.xml"),
        totalReports = Some(10)
      )

      mongoRepository.saveOrUpdate(submissionToStore, upsert = true).futureValue

      val submissionFromDb = mongoRepository.getByReference(submissionToStore.id).futureValue.value
      submissionFromDb.baCode shouldBe submissionToStore.baCode
    }

    "not return submission older 90 days" in {
      mongoRepository.collection.deleteMany(Document()).toFutureOption().futureValue

      val daysToSubtract = 91

      val submissionToStore = ReportStatus(
        UUID.randomUUID.toString,
        url = Option(s"http://localhost:2211/${UUID.randomUUID}"),
        checksum = Option("RandomCheckSum"),
        errors = Seq(Error(UNKNOWN_TYPE_OF_TAX, Seq("Some", "Parameters"))),
        baCode = "BA2020",
        status = Submitted.value,
        filename = Option("filename.xml"),
        totalReports = Some(10),
        createdAt = Instant.now.normalize
      )
      mongoRepository.saveOrUpdate(submissionToStore, upsert = true).futureValue
      mongoRepository.saveOrUpdate(
        submissionToStore.copy(id = UUID.randomUUID.toString, createdAt = Instant.now.minus(daysToSubtract, ChronoUnit.DAYS)),
        upsert = true
      ).futureValue

      val reports = mongoRepository.collection.countDocuments().toFutureOption().futureValue
      reports.getOrElse(0) shouldBe 2

      println("Waiting while expired submissions are removed by the MongoDB background process.")
      eventually(timeout(60 seconds), interval(2 seconds)) {
        mongoRepository.getByUser("BA2020", None).futureValue.value should have size 1
      }

      val submissionsFromDb = mongoRepository.getByUser("BA2020", None).futureValue.value
      submissionsFromDb should have size 1
      submissionsFromDb should contain only submissionToStore
    }
  }
