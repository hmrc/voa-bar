/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.voabar.modules

import com.google.inject.Provides
import com.google.inject.AbstractModule
import play.api.Configuration
import services.EbarsValidator
import uk.gov.hmrc.crypto.{ApplicationCrypto, Decrypter, Encrypter}
import uk.gov.hmrc.voabar.util.DataMonitor

class VoaBARModule extends AbstractModule {

  override def configure() = {
    bind(classOf[EbarsValidator]).toInstance(new EbarsValidator)
    bind(classOf[DataMonitor]).asEagerSingleton()
  }

  @Provides
  def jsonCryptoProvider(config: Configuration): Encrypter & Decrypter =
    new ApplicationCrypto(config.underlying).JsonCrypto

}
