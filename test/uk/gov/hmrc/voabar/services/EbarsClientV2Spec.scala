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

package uk.gov.hmrc.voabar.services

import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.testkit.NoMaterializer
import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.net.ConnectException
import scala.concurrent.ExecutionContext.Implicits._

/**
 * @author Yuriy Tumakha
 */
class EbarsClientV2Spec extends AnyWordSpec with should.Matchers with DefaultAwaitTimeout with FutureAwaits {

  implicit private val mat: Materializer = NoMaterializer

  "EbarsClientV2" should {
    "start with only proxy and voa-ebars service configuration" in {
      val configuration = Configuration(
        "proxy.enabled" -> true,
        "proxy.host" -> "localhost",
        "proxy.port" -> 9999,
        "proxy.username" -> "foo",
        "proxy.password" -> "bar",
        "microservice.services.voa-ebars.host" -> "localhost",
        "microservice.services.voa-ebars.port" -> 123456,
        "microservice.services.voa-ebars.protocol" -> "http"
      )
      val servicesConfig = new ServicesConfig(configuration)
      val ebarsClient = new EbarsClientV2(servicesConfig, configuration)

      val thrown = intercept[ConnectException] {
        await(ebarsClient.uploadXMl("", "", "<xml/>", 1))
      }
      thrown.getMessage shouldBe "Connection refused: localhost/127.0.0.1:9999"
    }
  }
}
