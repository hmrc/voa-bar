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

package uk.gov.hmrc.vo.autobars.connectors

import models.Purpose
import play.api.Configuration
import play.api.libs.json.Json
import play.api.test.Helpers.*
import uk.gov.hmrc.crypto.ApplicationCrypto
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.vo.autobars.util.Utils
import uk.gov.hmrc.vo.unit.test.BaseAppSpec

import java.net.URL

class EmailConnectorSpec extends BaseAppSpec:

  private val configuration = inject[Configuration]
  private val crypto        = ApplicationCrypto(configuration.underlying)
  private val utils         = Utils(crypto.JsonCrypto)
  private val username      = "username"
  private val password      = "password"
  private val baCode        = "BA1234"
  private val purpose       = Purpose.CT
  private val submissionId  = "submissionId"
  private val filename      = "filename.xml"
  private val date          = "2000-01-01"

  private def getConfiguration(sendEmail: Boolean = true): Configuration =
    Configuration(
      "microservice.services.email.host"     -> "localhost",
      "microservice.services.email.port"     -> "80",
      "microservice.services.email.protocol" -> "http",
      "needToSendEmail"                      -> sendEmail,
      "email"                                -> "foo@bar.co.uk"
    )

  "EmailConnector" should {
    "verify that the email service gets called when email needs to be sent" in {
      val httpClientV2Mock = httpClientMock(POST, responseBody = Json.parse("{}"))

      val configuration = getConfiguration()
      val connector     = DefaultEmailConnector(httpClientV2Mock, ServicesConfig(configuration), configuration, utils)

      connector.sendEmail(baCode, purpose, submissionId, username, password, filename, date, "")

      verify(httpClientV2Mock)
        .post(any[URL])(using any[HeaderCarrier])
    }

    "verify that the email service doesn't get called when email needn't to be sent" in {
      val httpClientV2Mock = mock[HttpClientV2]
      val configuration    = getConfiguration(sendEmail = false)
      val connector        = DefaultEmailConnector(httpClientV2Mock, ServicesConfig(configuration), configuration, utils)

      connector.sendEmail(baCode, purpose, submissionId, username, password, filename, date, "")

      verify(httpClientV2Mock, times(0))
        .post(any[URL])(using any[HeaderCarrier])
    }
  }
