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
import org.mockito.scalatest.MockitoSugar
import org.mongodb.scala.bson.collection.immutable.Document
import org.scalatest.{BeforeAndAfterAll, EitherValues}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.voabar.models.{BarMongoError, Done, Error, Failed, Pending, ReportStatus, Submitted}
import uk.gov.hmrc.voabar.repositories.SubmissionStatusRepositoryImpl
import uk.gov.hmrc.voabar.util.{CHARACTER, INVALID_XML_XSD, TIMEOUT_ERROR}

class SubmissionStatusRepositorySpec extends PlaySpec with BeforeAndAfterAll
  with EitherValues with DefaultAwaitTimeout with FutureAwaits  with GuiceOneAppPerSuite with MockitoSugar {

  override def fakeApplication() = new GuiceApplicationBuilder()
    .configure("mongodb.uri" -> ("mongodb://localhost:27017/voa-bar" + UUID.randomUUID().toString))
    .build()

  lazy val mongoComponent = app.injector.instanceOf[MongoComponent]


  val repo = app.injector.instanceOf[SubmissionStatusRepositoryImpl]

  "repository" should {

    "add error" in {
      val submissionId = "111"
      await(repo.collection.insertOne(ReportStatus(submissionId, ZonedDateTime.now)).toFutureOption())

      val reportStatusError = Error(CHARACTER , Seq( "message", "detail"))

      val dbResult = await(repo.addError(submissionId, reportStatusError))

      dbResult mustBe Symbol("right")

      val submission = await(repo.getByReference(submissionId))
      println(submission)
    }

    "add error without description" in {
      await(repo.collection.insertOne(ReportStatus("ggggg", ZonedDateTime.now)).toFutureOption())

      val reportStatusError = Error(CHARACTER , List())

      val dbResult = await(repo.addError("ggggg", reportStatusError))

      dbResult mustBe Symbol("right")
    }

    "update status" in {
      await(repo.collection.insertOne(ReportStatus("222", ZonedDateTime.now)).toFutureOption())

      val dbResult = await(repo.updateStatus("222", Submitted))

      dbResult mustBe Symbol("right")
    }

    "failed for nonExisting UUID" in {
        val dbResult = await(repo.updateStatus("nonExistingSubmissionID", Submitted))

        dbResult mustBe Symbol("left")
        dbResult mustBe Left(BarMongoError("Report status wasn't updated for nonExistingSubmissionID"))
    }

    "serialise and deserialize ReportStatus" in {

      val dateTime = ZonedDateTime.now

      val guid = UUID.randomUUID().toString

      val reportStatus = ReportStatus(guid, dateTime, None, None, Seq(), Seq.empty, Option("BA2220"), Some(Failed.value))

      await(repo.collection.insertOne(reportStatus).toFutureOption())


      val res = await(repo.getByReference(guid))

      res mustBe Symbol("right")

      res.value mustBe reportStatus
    }

    "Change status to failed for submission after timeout" in {

      val report = aReport().copy(created = ZonedDateTime.now.minusMinutes(121))

      await(repo.saveOrUpdate(report, true))

      val reportFromDb = await(repo.getByReference(report.id))

      reportFromDb.value.status.value mustBe Failed.value

      reportFromDb.value.errors mustBe Seq(Error(TIMEOUT_ERROR))
    }

    "Not change status or anything else for final submission state" in {
      import org.scalatest.prop.TableDrivenPropertyChecks._
      val finalStates = Table(("Final state", "errors"),
        (Submitted.value, Seq()),
        (Done.value, Seq()),
        (Failed.value, Seq(Error(INVALID_XML_XSD, Seq("Additional", "Parameters"))))
      )

      forAll (finalStates) { case (finalState: String, errors: Seq[Error]) =>
        val report = aReport().copy(created = ZonedDateTime.now.minusDays(21), status = Option(finalState), errors = errors)

        await(repo.collection.insertOne(report).toFutureOption())

        val reportFromDb = await(repo.getByReference(report.id))

        reportFromDb.value.status.value mustBe finalState

        reportFromDb.value.errors mustBe errors

        reportFromDb.value mustBe report
      }
    }

    "Save baCode when saving or updating submission" in {
      import uk.gov.hmrc.voabar.util._

      val submissionToStore = ReportStatus(UUID.randomUUID().toString, ZonedDateTime.now,
        Option(s"http://localhost:2211/${UUID.randomUUID()}"), Option("RandomCheckSum"),
        Seq(Error(UNKNOWN_TYPE_OF_TAX, Seq("Some", "Parameters"))),
        Seq.empty,
        Option("BA2020"),
        Option(Submitted.value),
        Option("filename.xml"),
        Some(10),
      )
      await(repo.saveOrUpdate(submissionToStore,true))
      val submissionFromDb = await(repo.getByReference(submissionToStore.id)).value
      submissionFromDb.baCode.value mustBe submissionToStore.baCode.get
    }

    "Not return submission older 90 days" in {
      import uk.gov.hmrc.voabar.util._

      await(repo.collection.deleteMany(Document()).toFutureOption())

      val submissionToStore = ReportStatus(UUID.randomUUID().toString, ZonedDateTime.now,
        Option(s"http://localhost:2211/${UUID.randomUUID()}"), Option("RandomCheckSum"),
        Seq(Error(UNKNOWN_TYPE_OF_TAX, Seq("Some", "Parameters"))),
        Seq.empty,
        Option("BA2020"),
        Option(Submitted.value),
        Option("filename.xml"),
        Some(10),
      )
      await(repo.saveOrUpdate(submissionToStore,true))
      await(repo.saveOrUpdate(submissionToStore.copy(id = UUID.randomUUID().toString, created = ZonedDateTime.now.minusDays(91))
        ,true))

      val reports = await(repo.collection.countDocuments().toFutureOption())

      val submissionsFromDb = await(repo.getByUser("BA2020", None)).value

      reports.value mustBe 2

      submissionsFromDb must have size 1
      submissionsFromDb must contain only submissionToStore
    }
  }

  def aReport(): ReportStatus =
    ReportStatus(UUID.randomUUID().toString, ZonedDateTime.now, None, None, Seq.empty, Seq.empty, Option("BA1010"), Some(Pending.value), None, None, None)

  override protected def afterAll(): Unit = {
    await(mongoComponent.database.drop().toFutureOption())
    mongoComponent.client.close()
  }

}
