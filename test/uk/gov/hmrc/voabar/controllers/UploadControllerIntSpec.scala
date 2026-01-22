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

package uk.gov.hmrc.voabar.controllers

import org.apache.commons.io.IOUtils
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.mongodb.scala.SingleObservableFuture
import org.scalatest.{BeforeAndAfterAll, EitherValues, OptionValues}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.Status.OK
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.{DefaultAwaitTimeout, FakeRequest, FutureAwaits}
import uk.gov.hmrc.crypto.{ApplicationCrypto, PlainText}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.voabar.connectors.{UpscanConnector, VoaEbarsConnector}
import uk.gov.hmrc.voabar.models.EbarsRequests.BAReportRequest
import uk.gov.hmrc.voabar.models.{BarError, ReportStatus, UploadDetails}
import uk.gov.hmrc.voabar.repositories.SubmissionStatusRepositoryImpl
import uk.gov.hmrc.voabar.util.PlayMongoUtil.byId

import java.net.URI
import java.nio.file.Paths
import java.util.concurrent.TimeUnit.SECONDS
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

class UploadControllerIntSpec
  extends PlaySpec
  with BeforeAndAfterAll
  with OptionValues
  with EitherValues
  with DefaultAwaitTimeout
  with FutureAwaits
  with GuiceOneAppPerSuite
  with MockitoSugar {

  private val voaEbarsConnector = mock[VoaEbarsConnector]

  when(voaEbarsConnector.sendBAReport(any[BAReportRequest])(using any[ExecutionContext], any[HeaderCarrier]))
    .thenAnswer(_ => Future.successful(OK))

  override def fakeApplication(): Application = new GuiceApplicationBuilder()
    .configure("mongodb.uri" -> "mongodb://localhost:27017/voa-bar")
    .bindings(
      bind[VoaEbarsConnector].to(voaEbarsConnector),
      bind[UpscanConnector].to[UploadControllerIntSpecUpscanConnector]
    )
    .build()

  private val controller           = app.injector.instanceOf[UploadController]
  private val mongoComponent       = app.injector.instanceOf[MongoComponent]
  private val submissionRepository = app.injector.instanceOf[SubmissionStatusRepositoryImpl]
  private val configuration        = app.injector.instanceOf[play.api.Configuration]

  private val crypto = new ApplicationCrypto(configuration.underlying).JsonCrypto

  private def fakeRequestWithXML = {

    val xmlURL = Paths.get("test/resources/xml/CTValid1.xml").toAbsolutePath.toUri.toURL.toString

    FakeRequest("POST", "/voa-bar/upload")
      .withHeaders(
        "BA-Code"  -> "BA5090",
        "password" -> crypto.encrypt(PlainText("BA5090")).value
      )
      .withBody(UploadDetails("1234", xmlURL))
  }

  "Upload controller " must {

    "properly handle correct XML " in {
      await(submissionRepository.collection.deleteOne(byId("1234")).toFutureOption())

      val reportStatus = ReportStatus("1234", baCode = "BA5090")

      await(submissionRepository.saveOrUpdate(reportStatus, upsert = true))

      controller.upload()(fakeRequestWithXML)

      SECONDS.sleep(2)

      val report = await(submissionRepository.getByReference("1234"))

      report mustBe Symbol("right")

      Console.println(report)

      report.value.status mustBe "Done"
    }

  }

  override protected def afterAll(): Unit =
    mongoComponent.client.close()

}

@Singleton
class UploadControllerIntSpecUpscanConnector @Inject() (implicit ec: ExecutionContext) extends UpscanConnector {

  override def downloadReport(url: String)(implicit hc: HeaderCarrier): Future[Either[BarError, Array[Byte]]] =
    Future(Right(IOUtils.toByteArray(new URI(url).toURL.openStream())))
}
