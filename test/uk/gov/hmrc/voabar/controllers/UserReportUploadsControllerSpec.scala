/*
 * Copyright 2018 HM Revenue & Customs
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

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import play.api.test.FakeRequest
import uk.gov.hmrc.voabar.repositories.{UserReportUpload, UserReportUploadsRepository}
import play.api.test.Helpers.{status, _}
import org.mockito.Mockito.when
import org.mockito.Matchers.any
import uk.gov.hmrc.voabar.models.BarMongoError

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UserReportUploadsControllerSpec extends PlaySpec with MockitoSugar {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  val error = BarMongoError("error")
  val id = "id"
  val userReportUpload = UserReportUpload(
    _id = id,
    userId = "userId",
    userPassword = "pass"
  )
  val json = Json.toJson(userReportUpload)
  val fakeRequest = FakeRequest("", "").withBody(json)
  "UserReportUploadsController" should {
    "save a user report upload successfully" in {
      val userReportUploadsRepositoryMock = mock[UserReportUploadsRepository]
      when(userReportUploadsRepositoryMock.save(any[UserReportUpload])) thenReturn Future.successful(Right(Unit))
      val userReportUploadsController = new UserReportUploadsController(userReportUploadsRepositoryMock)

      val response = userReportUploadsController.save()(fakeRequest)

      status(response) mustBe NO_CONTENT
    }
    "return an error status when saving fails" in {
      val userReportUploadsRepositoryMock = mock[UserReportUploadsRepository]
      when(userReportUploadsRepositoryMock.save(any[UserReportUpload])) thenReturn Future.successful(Left(error))
      val userReportUploadsController = new UserReportUploadsController(userReportUploadsRepositoryMock)

      val response = userReportUploadsController.save()(fakeRequest)

      status(response) mustBe INTERNAL_SERVER_ERROR
    }
    "get by id returns user data correctly" in {
      val userReportUploadsRepositoryMock = mock[UserReportUploadsRepository]
      when(userReportUploadsRepositoryMock.getById(any[String])) thenReturn Future.successful(Right(Some(userReportUpload)))
      val userReportUploadsController = new UserReportUploadsController(userReportUploadsRepositoryMock)

      val response = userReportUploadsController.getById(id)(fakeRequest).run()

      status(response) mustBe OK
      contentAsJson(response) mustBe json
    }
    "return bad status if an error occurs" in {
      val userReportUploadsRepositoryMock = mock[UserReportUploadsRepository]
      when(userReportUploadsRepositoryMock.getById(any[String])) thenReturn Future.successful(Left(error))
      val userReportUploadsController = new UserReportUploadsController(userReportUploadsRepositoryMock)

      val response = userReportUploadsController.getById(id)(fakeRequest).run()

      status(response) mustBe INTERNAL_SERVER_ERROR
    }
  }
}
