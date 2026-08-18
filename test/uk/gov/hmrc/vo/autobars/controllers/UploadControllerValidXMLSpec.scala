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

package uk.gov.hmrc.vo.autobars.controllers

import org.apache.commons.io.IOUtils
import org.mongodb.scala.SingleObservableFuture
import play.api.http.Status.OK
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest
import play.api.{Application, Configuration}
import uk.gov.hmrc.crypto.{ApplicationCrypto, PlainText}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.vo.autobars.connectors.{UpscanConnector, VOEbarsConnector}
import uk.gov.hmrc.vo.autobars.models.EbarsRequests.BAReportRequest
import uk.gov.hmrc.vo.autobars.models.{BarError, ReportStatus, UploadDetails}
import uk.gov.hmrc.vo.autobars.repositories.SubmissionStatusRepositoryImpl
import uk.gov.hmrc.vo.autobars.util.PlayMongoUtil.byId
import uk.gov.hmrc.vo.unit.test.BaseAppSpec

import java.net.URI
import java.nio.file.Paths
import java.util.concurrent.TimeUnit.SECONDS
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

class UploadControllerValidXMLSpec extends BaseAppSpec:

  override def fakeApplication(): Application =
    val voEbarsConnector = mock[VOEbarsConnector]

    when(voEbarsConnector.sendBAReport(any[BAReportRequest])(using any[ExecutionContext], any[HeaderCarrier]))
      .thenAnswer(_ => Future.successful(OK))

    GuiceApplicationBuilder()
      .configure("mongodb.uri" -> "mongodb://localhost:27017/voa-bar")
      .bindings(
        bind[VOEbarsConnector].to(voEbarsConnector),
        bind[UpscanConnector].to[TestUpscanConnector]
      )
      .build()

  private val controller           = inject[UploadController]
  private val mongoComponent       = inject[MongoComponent]
  private val submissionRepository = inject[SubmissionStatusRepositoryImpl]
  private val configuration        = inject[Configuration]

  private val crypto = ApplicationCrypto(configuration.underlying).JsonCrypto

  private def fakeRequestWithXML =
    val xmlURL = Paths.get("test/resources/xml/CTValid1.xml").toAbsolutePath.toUri.toURL.toString

    FakeRequest("POST", "/voa-bar/upload")
      .withHeaders(
        "BA-Code"  -> "BA5090",
        "password" -> crypto.encrypt(PlainText("BA5090")).value
      )
      .withBody(UploadDetails("1234", xmlURL))

  "Upload controller " should {
    "properly handle correct XML " in {
      submissionRepository.collection.deleteOne(byId("1234")).toFutureOption().futureValue

      val reportStatus = ReportStatus("1234", baCode = "BA5090")

      submissionRepository.saveOrUpdate(reportStatus, upsert = true).futureValue

      controller.upload()(fakeRequestWithXML)

      SECONDS.sleep(2)

      val report = submissionRepository.getByReference("1234").futureValue

      report shouldBe Symbol("right")

      Console.println(report)

      report.value.status shouldBe "Done"
    }
  }

  override protected def afterAll(): Unit =
    mongoComponent.client.close()

@Singleton
class TestUpscanConnector @Inject() (implicit ec: ExecutionContext) extends UpscanConnector:

  override def downloadReport(url: String)(using hc: HeaderCarrier): Future[Either[BarError, Array[Byte]]] =
    Future(Right(IOUtils.toByteArray(URI(url).toURL.openStream())))
