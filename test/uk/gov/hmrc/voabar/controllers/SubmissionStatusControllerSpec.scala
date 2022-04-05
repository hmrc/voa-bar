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

package uk.gov.hmrc.voabar.controllers

import java.time.ZonedDateTime
import java.util.UUID
import akka.stream.Materializer
import akka.stream.testkit.NoMaterializer
import com.typesafe.config.ConfigFactory
import org.mockito.scalatest.MockitoSugar
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.voabar.repositories.SubmissionStatusRepository
import play.api.Configuration
import play.api.libs.json.Json
import play.api.test.FakeRequest
import uk.gov.hmrc.voabar.models.{BarMongoError, ReportStatus}
import play.api.test.Helpers._
import uk.gov.hmrc.crypto.{ApplicationCrypto, PlainText}
import uk.gov.hmrc.voabar.services.WebBarsService

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class SubmissionStatusControllerSpec extends PlaySpec with MockitoSugar {

  implicit val materializer: Materializer = NoMaterializer

  val id = "id"
  val date = ZonedDateTime.now
  val userId = "userId"
  val reportStatus = ReportStatus(
    id = id,
    created = date,
    url = Some("url.com"),
    baCode = Some(userId)
  )
  val configuration = Configuration(ConfigFactory.load())
  val crypto = new ApplicationCrypto(configuration.underlying).JsonCrypto
  val reportStatusJson = Json.toJson(reportStatus)
  val reportStatusesJson = Json.toJson(Seq(reportStatus))
  val fakeRequest = FakeRequest("", "").withBody(reportStatusJson).withHeaders(("BA-Code", userId), ("password" ->  crypto.encrypt(PlainText("gggg")).value))
  val error = BarMongoError("error")

  val webBarsServiceMock = mock[WebBarsService]

  "SubmissionStatusController" should {
    "save a new report status successfully" in {
      val submissionStatusRepositoryMock = mock[SubmissionStatusRepository]
      when(submissionStatusRepositoryMock.saveOrUpdate(any[ReportStatus], any[Boolean])) thenReturn(Future.successful(Right(Unit)))
      val submissionStatusController = new SubmissionStatusController(submissionStatusRepositoryMock, stubControllerComponents(), webBarsServiceMock, configuration)

      val response = submissionStatusController.save()(fakeRequest)

      status(response) mustBe NO_CONTENT
    }
    "return invalid status when saving fails" in {
      val submissionStatusRepositoryMock = mock[SubmissionStatusRepository]
      when(submissionStatusRepositoryMock.saveOrUpdate(any[ReportStatus], any[Boolean])) thenReturn(Future.successful(Left(error)))
      val submissionStatusController = new SubmissionStatusController(submissionStatusRepositoryMock, stubControllerComponents(), webBarsServiceMock, configuration)

      val response = submissionStatusController.save()(fakeRequest)

      status(response) mustBe INTERNAL_SERVER_ERROR
    }
    "save a new report status user info successfully" in {
      val submissionStatusRepositoryMock = mock[SubmissionStatusRepository]
      when(submissionStatusRepositoryMock.saveOrUpdate(any[String], any[String])) thenReturn(Future.successful(Right(Unit)))
      val submissionStatusController = new SubmissionStatusController(submissionStatusRepositoryMock, stubControllerComponents(), webBarsServiceMock, configuration)

      val response = submissionStatusController.saveUserInfo()(fakeRequest)

      status(response) mustBe NO_CONTENT
    }
    "return invalid status when saving user info fails" in {
      val submissionStatusRepositoryMock = mock[SubmissionStatusRepository]
      when(submissionStatusRepositoryMock.saveOrUpdate(any[String], any[String])) thenReturn(Future.successful(Left(error)))
      val submissionStatusController = new SubmissionStatusController(submissionStatusRepositoryMock, stubControllerComponents(), webBarsServiceMock, configuration)

      val response = submissionStatusController.saveUserInfo()(fakeRequest)

      status(response) mustBe INTERNAL_SERVER_ERROR
    }
    "returns report statuses when search by user id" in {
      val submissionStatusRepositoryMock = mock[SubmissionStatusRepository]
      when(submissionStatusRepositoryMock.getByUser(any[String], any[Option[String]])) thenReturn(Future.successful(Right(Seq(reportStatus))))
      val submissionStatusController = new SubmissionStatusController(submissionStatusRepositoryMock, stubControllerComponents(), webBarsServiceMock, configuration)

      val response = submissionStatusController.getByUser()(fakeRequest)

      status(response) mustBe OK
      contentAsJson(response) mustBe reportStatusesJson
    }
    "returns invalid error when search by user id unsuccessfully" in {
      val submissionStatusRepositoryMock = mock[SubmissionStatusRepository]
      when(submissionStatusRepositoryMock.getByUser(any[String], any[Option[String]])) thenReturn(Future.successful(Left(error)))
      val submissionStatusController = new SubmissionStatusController(submissionStatusRepositoryMock, stubControllerComponents(), webBarsServiceMock, configuration)

      val response = submissionStatusController.getByUser()(fakeRequest)

      status(response) mustBe INTERNAL_SERVER_ERROR
    }
    "returns report statuses when search by submission id" in {
      val submissionStatusRepositoryMock = mock[SubmissionStatusRepository]
      when(submissionStatusRepositoryMock.getByReference(any[String])) thenReturn(Future.successful(Right(reportStatus)))
      val submissionStatusController = new SubmissionStatusController(submissionStatusRepositoryMock, stubControllerComponents(), webBarsServiceMock, configuration)

      val response = submissionStatusController.getByReference(id)(fakeRequest)

      status(response) mustBe OK
      contentAsJson(response) mustBe reportStatusJson
    }
    "returns invalid error when search by submission id unsuccessfully" in {
      val submissionStatusRepositoryMock = mock[SubmissionStatusRepository]
      when(submissionStatusRepositoryMock.getByReference(any[String])) thenReturn(Future.successful(Left(error)))
      val submissionStatusController = new SubmissionStatusController(submissionStatusRepositoryMock, stubControllerComponents(), webBarsServiceMock, configuration)

      val response = submissionStatusController.getByReference(id)(fakeRequest)

      status(response) mustBe INTERNAL_SERVER_ERROR
    }
    "returns all report statuses" in {
      val submissionStatusRepositoryMock = mock[SubmissionStatusRepository]
      when(submissionStatusRepositoryMock.getAll()) thenReturn (Future.successful(Right(Seq(reportStatus))))
      val submissionStatusController = new SubmissionStatusController(submissionStatusRepositoryMock, stubControllerComponents(), webBarsServiceMock, configuration)

      val response = submissionStatusController.getAll()(fakeRequest)

      status(response) mustBe OK
      contentAsJson(response) mustBe reportStatusesJson
    }

    "returns invalid error when search all unsuccessfully" in {
      val submissionStatusRepositoryMock = mock[SubmissionStatusRepository]
      when(submissionStatusRepositoryMock.getAll()) thenReturn(Future.successful(Left(error)))
      val submissionStatusController = new SubmissionStatusController(submissionStatusRepositoryMock, stubControllerComponents(), webBarsServiceMock, configuration)

      val response = submissionStatusController.getAll()(fakeRequest)

      status(response) mustBe INTERNAL_SERVER_ERROR
    }

    "delete submission and return delete status" in {
      val reference = UUID.randomUUID().toString
      val deleteResult = Json.obj(
        "code" -> Option.empty[String],
        "n" -> 1,
        "writeErrors" -> "",
        "writeConcernError" -> ""
      )
      val submissionStatusRepositoryMock = mock[SubmissionStatusRepository]
      when(submissionStatusRepositoryMock.deleteByReference(any[String], any[String])) thenReturn(Future.successful(Right(deleteResult)))
      val submissionStatusController = new SubmissionStatusController(submissionStatusRepositoryMock, stubControllerComponents(), webBarsServiceMock, configuration)

      val response = submissionStatusController.deleteByReference(reference).apply(fakeRequest.withHeaders("BA-Code" -> "BA1010")).run()

      status(response) mustBe OK
      contentAsJson(response) mustBe deleteResult
    }

    "Reject deletion when BA-Code is not in http header" in {
      val reference = UUID.randomUUID().toString
      val submissionStatusRepositoryMock = mock[SubmissionStatusRepository]
      val submissionStatusController = new SubmissionStatusController(submissionStatusRepositoryMock, stubControllerComponents(), webBarsServiceMock, configuration)

      val response = submissionStatusController.deleteByReference(reference).apply(fakeRequest.withHeaders(fakeRequest.headers.remove("BA-Code"))).run()

      status(response) mustBe UNAUTHORIZED
    }

  }
}
