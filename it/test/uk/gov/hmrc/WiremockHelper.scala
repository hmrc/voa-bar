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

package uk.gov.hmrc

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration

trait WiremockHelper {

  def withWiremock[T](stubbing: WireMockServer => Unit)(block: Int => T): T = {

    val wireMockServer = new WireMockServer(WireMockConfiguration
      .options()
      .dynamicPort())
    wireMockServer.start()
    stubbing(wireMockServer)
    val result         = block(wireMockServer.port())
    wireMockServer.stop()
    result
  }

  def withWiremockServer[T](stubbing: WireMockServer => Unit)(block: (Int, WireMockServer) => T): T = {

    val wireMockServer = new WireMockServer(WireMockConfiguration
      .options()
      .dynamicPort())
    wireMockServer.start()
    stubbing(wireMockServer)
    val result         = block(wireMockServer.port(), wireMockServer)
    wireMockServer.stop()
    result
  }

}
