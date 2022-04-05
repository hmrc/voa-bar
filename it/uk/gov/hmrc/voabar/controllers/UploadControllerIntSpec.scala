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

import java.net.URL
import java.nio.file.Paths
import java.time.ZonedDateTime
import javax.inject.{Inject, Singleton}
import org.apache.commons.io.IOUtils
import org.mockito.scalatest.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, EitherValues, OptionValues}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.{DefaultAwaitTimeout, FakeRequest, FutureAwaits}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.voabar.util.PlayMongoUtil.byId
import uk.gov.hmrc.voabar.connectors.{LegacyConnector, UpscanConnector}
import org.mockito.invocation.InvocationOnMock
import play.api.http.Status.OK
import uk.gov.hmrc.voabar.models.EbarsRequests.BAReportRequest
import play.api.inject.bind
import uk.gov.hmrc.crypto.{ApplicationCrypto, PlainText}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.voabar.models.{BarError, ReportStatus, UploadDetails}
import uk.gov.hmrc.voabar.repositories.SubmissionStatusRepositoryImpl

import scala.concurrent.{ExecutionContext, Future}


class UploadControllerIntSpec extends PlaySpec with BeforeAndAfterAll with OptionValues
  with EitherValues with DefaultAwaitTimeout with FutureAwaits with GuiceOneAppPerSuite  with MockitoSugar {

  val legacyConnector = mock[LegacyConnector]

  when(legacyConnector.sendBAReport(any[BAReportRequest])(any[ExecutionContext], any[HeaderCarrier]))
    .thenAnswer[InvocationOnMock](_ => Future.successful(OK))

  override def fakeApplication() = new GuiceApplicationBuilder()
    .configure("mongodb.uri" -> ("mongodb://localhost:27017/voa-bar"))
    .bindings(
      bind[LegacyConnector].to(legacyConnector),
      bind[UpscanConnector].to[UploadControllerIntSpecUpscanConnector]
    )
    .build()

  lazy val controller = app.injector.instanceOf[UploadController]
  lazy val mongoComponent = app.injector.instanceOf[MongoComponent]
  lazy val submissionRepository = app.injector.instanceOf[SubmissionStatusRepositoryImpl]
  lazy val configuration = app.injector.instanceOf[play.api.Configuration]

  lazy val crypto = new ApplicationCrypto(configuration.underlying).JsonCrypto

  def fakeRequestWithXML = {

    val xmlURL = Paths.get("test/resources/xml/CTValid1.xml").toAbsolutePath.toUri.toURL.toString

    FakeRequest("POST", "/voa-bar/upload")
      .withHeaders(
        "BA-Code" -> "BA5090",
        "password" -> crypto.encrypt(PlainText("BA5090")).value
      )
      .withBody(UploadDetails("1234", xmlURL))
  }


  "Upload controller " must {

    "properly handle correct XML " in {
      await(submissionRepository.collection.deleteOne(byId("1234")).toFutureOption())

      val reportStatus = ReportStatus("1234", ZonedDateTime.now, baCode = Option("BA5090"))

      await(submissionRepository.saveOrUpdate(reportStatus, true))

      controller.upload()(fakeRequestWithXML)

      Thread.sleep(3000)

      val report = await(submissionRepository.getByReference("1234"))

      report must be('right)

      Console.println(report)

      report.value.status.value mustBe "Done"
    }

  }

  override protected def afterAll(): Unit = {
    mongoComponent.client.close()
  }

}

@Singleton
class UploadControllerIntSpecUpscanConnector @Inject() (implicit ec: ExecutionContext) extends UpscanConnector {

  override def downloadReport(url: String)(implicit hc: HeaderCarrier): Future[Either[BarError, Array[Byte]]] = {
    Future(Right(IOUtils.toByteArray(new URL(url).openStream())))
  }
}
