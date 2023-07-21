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

package uk.gov.hmrc.voabar.controllers

import org.mockito.invocation.InvocationOnMock
import org.mockito.scalatest.MockitoSugar
import org.scalatest.concurrent.Eventually
import org.scalatest.time.SpanSugar
import org.scalatest.{EitherValues, OptionValues}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.{Application, Configuration}
import play.api.http.Status
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Helpers.{contentAsString, status}
import play.api.test.{DefaultAwaitTimeout, FakeRequest, FutureAwaits, Injecting}
import uk.gov.hmrc.crypto.{ApplicationCrypto, PlainText}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.voabar.connectors.{LegacyConnector, UpscanConnector}
import uk.gov.hmrc.voabar.models.EbarsRequests.BAReportRequest
import uk.gov.hmrc.voabar.models.UpScanRequests.{FailureDetails, UploadConfirmation, UploadConfirmationError, UploadDetails}
import uk.gov.hmrc.voabar.models.{BarError, Done, Error, Failed, Submitted}
import uk.gov.hmrc.voabar.repositories.{DefaultUserReportUploadsRepository, SubmissionStatusRepositoryImpl, UserReportUpload}
import uk.gov.hmrc.voabar.util.PlayMongoUtil.byId
import uk.gov.hmrc.voabar.util.{BA_CODE_MATCH, UPSCAN_ERROR}

import java.net.URL
import java.nio.file.Paths
import java.time.OffsetDateTime
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.Using

/**
 * @author Yuriy Tumakha
 */
class UpscanCallbackControllerISpec extends PlaySpec with OptionValues with EitherValues with Eventually with SpanSugar
  with DefaultAwaitTimeout with FutureAwaits with GuiceOneAppPerSuite with MockitoSugar with Status with Injecting {

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(timeout = 9 seconds, interval = 1 second)

  override def fakeApplication(): Application = {
    val legacyConnector = mock[LegacyConnector]

    when(legacyConnector.sendBAReport(any[BAReportRequest])(any[ExecutionContext], any[HeaderCarrier]))
      .thenAnswer[InvocationOnMock](_ => Future.successful(OK))

    new GuiceApplicationBuilder()
      .bindings(
        bind[LegacyConnector].to(legacyConnector),
        bind[UpscanConnector].to(StubUpscanConnector)
      )
      .build()
  }

  private val controller = inject[UpscanCallbackController]

  private val submissionRepository = inject[SubmissionStatusRepositoryImpl]
  private val userReportUploadsRepository = inject[DefaultUserReportUploadsRepository]
  private val configuration = inject[Configuration]
  private val crypto = new ApplicationCrypto(configuration.underlying).JsonCrypto

  private val xmlUrl = Paths.get("test/resources/xml/CTValid1.xml").toAbsolutePath.toUri.toURL.toString
  private val reference = "111-777"
  private val reference2 = "222-777"
  private val reference3 = "333-777"
  private val username = "BA5090"
  private val password = crypto.encrypt(PlainText(username)).value

  "UpscanCallbackController " must {
    "handle upscan callback with `UploadConfirmation`" in {
      await(submissionRepository.collection.deleteOne(byId(reference)).toFutureOption())
      await(userReportUploadsRepository.collection.deleteOne(byId(reference)).toFutureOption())
      await(userReportUploadsRepository.save(UserReportUpload(reference, username, password)))

      val request = FakeRequest("POST", "/voa-bar/upload/confirmation")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.toJson(
          UploadConfirmation(reference, xmlUrl, "READY",
            UploadDetails(OffsetDateTime.now, "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100", "application/xml", "CTValid1.xml")
          )
        ))

      val result = controller.onConfirmation(request)
      status(result) mustBe ACCEPTED
      contentAsString(result) mustBe ""

      eventually {
        await(submissionRepository.getByReference(reference)).value.status.value must not be Submitted.value
      }

      val submissionReport = await(submissionRepository.getByReference(reference))

      submissionReport.isRight mustBe true
      submissionReport.value.status.value mustBe Done.value
      submissionReport.value.baCode.value mustBe username
    }

    "handle upscan callback with `UploadConfirmationError`" in {
      await(submissionRepository.collection.deleteOne(byId(reference2)).toFutureOption())
      await(userReportUploadsRepository.collection.deleteOne(byId(reference2)).toFutureOption())
      await(userReportUploadsRepository.save(UserReportUpload(reference2, username, password)))

      val request = FakeRequest("POST", "/voa-bar/upload/confirmation")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.toJson(
          UploadConfirmationError(reference2, "FAILED",
            FailureDetails("QUARANTINE", "This file has a virus")
          )
        ))

      val result = controller.onConfirmation(request)
      status(result) mustBe ACCEPTED
      contentAsString(result) mustBe ""

      eventually {
        await(submissionRepository.getByReference(reference2)).value.status.value must not be Submitted.value
      }

      val submissionReport = await(submissionRepository.getByReference(reference2))

      submissionReport.value.status.value mustBe Failed.value
      submissionReport.value.baCode.value mustBe username
      submissionReport.value.errors mustBe Seq(Error(UPSCAN_ERROR, Seq("QUARANTINE")))
    }

    "return error BA_CODE_MATCH when different code in file and in db" in {
      await(submissionRepository.collection.deleteOne(byId(reference3)).toFutureOption())
      await(userReportUploadsRepository.collection.deleteOne(byId(reference3)).toFutureOption())
      await(userReportUploadsRepository.save(UserReportUpload(reference3, "BA4444", password)))

      val request = FakeRequest("POST", "/voa-bar/upload/confirmation")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.toJson(
          UploadConfirmation(reference3, xmlUrl, "READY",
            UploadDetails(OffsetDateTime.now, "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100", "application/xml", "CTValid1.xml")
          )
        ))

      val result = controller.onConfirmation(request)
      status(result) mustBe ACCEPTED
      contentAsString(result) mustBe ""

      eventually {
        await(submissionRepository.getByReference(reference3)).value.status.value must not be Submitted.value
      }

      val submissionReport = await(submissionRepository.getByReference(reference3))

      submissionReport.value.status.value mustBe Failed.value
      submissionReport.value.baCode.value mustBe "BA4444"
      submissionReport.value.errors mustBe Seq(Error(BA_CODE_MATCH, Seq("5090")))
    }

    "return 400 BAD_REQUEST for bad json" in {
      val request = FakeRequest("POST", "/voa-bar/upload/confirmation")
        .withHeaders(
          "Content-Type" -> "application/json"
        )
        .withBody(Json.obj("bad" -> "json"))

      val result = controller.onConfirmation(request)
      status(result) mustBe BAD_REQUEST
      contentAsString(result) mustBe "Unable to parse request"
    }
  }

}

object StubUpscanConnector extends UpscanConnector {

  override def downloadReport(url: String)(implicit hc: HeaderCarrier): Future[Either[BarError, Array[Byte]]] =
    Future.successful(Right(
      Using.resource(new URL(url).openStream()) {
        _.readAllBytes()
      }
    ))

}
