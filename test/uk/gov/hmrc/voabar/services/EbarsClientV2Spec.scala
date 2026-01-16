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

package uk.gov.hmrc.voabar.services

import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import org.mockito.ArgumentMatchers.any

import java.net.URL
import scala.concurrent.ExecutionContext.Implicits.*
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status.OK
import uk.gov.hmrc.voabar.connectors.RequestBuilderStub

import scala.util.Failure

/**
  * @author Yuriy Tumakha
  */
class EbarsClientV2Spec extends AnyWordSpec with should.Matchers with DefaultAwaitTimeout with FutureAwaits with MockitoSugar:

  "EbarsClientV2" should {
    "start with only proxy and voa-ebars service configuration" in {
      val configuration  = Configuration(
        "http-verbs.proxy.enabled"                 -> true,
        "proxy.host"                               -> "localhost",
        "proxy.port"                               -> 9999,
        "proxy.username"                           -> "foo",
        "proxy.password"                           -> "bar",
        "microservice.services.voa-ebars.host"     -> "localhost",
        "microservice.services.voa-ebars.port"     -> 123456,
        "microservice.services.voa-ebars.protocol" -> "http"
      )
      val servicesConfig = new ServicesConfig(configuration)

      val httpClientV2Mock = mock[HttpClientV2]
      when(
        httpClientV2Mock.post(any[URL])(using any[HeaderCarrier])
      ).thenReturn(RequestBuilderStub(Right(OK), ""))

      val ebarsClient = new EbarsClientV2(httpClientV2Mock, servicesConfig)

      await(ebarsClient.uploadXMl("user", "pass", "<xml/>", 1)) shouldBe Failure(
        EbarsApiError(500, "Parsing eBars response failed. attempt: 1")
      )
    }
  }
