/*
 * Copyright 2019 HM Revenue & Customs
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
import net.codingwell.scalaguice.ScalaModule
import play.api.Play
import services.EbarsValidator
import javax.inject.Inject
import uk.gov.hmrc.crypto.{ApplicationCrypto, CompositeSymmetricCrypto}

class VoaBARModule  extends ScalaModule {
  override def configure() = {
    bind[EbarsValidator].toInstance(new EbarsValidator)

  }

  @Provides
  def jsonCryptoProvider(): CompositeSymmetricCrypto = {
    //applicationCrypto.JsonCrypto
    new ApplicationCrypto(Play.current.configuration.underlying).JsonCrypto
  }
}
