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

import org.scalatestplus.play.PlaySpec

class ResourceResolverSpec extends PlaySpec {
  val resolver        = new ResourceResolver
  val existingFile    = "BS7666-v2-0.xsd"
  val nonExistingFile = "file.xsd"

  "The resource resolver " must {

    "Given a valid systemId representing an existing file in the /resources/xsd/ folder should return an input" in {
      val result = resolver.resolveResource("type", "namespace", "publicID", existingFile, "publicURI")
      result.isInstanceOf[Input] mustBe true
    }

    "Throw an exception if the given file name doesn't exists in the specified path" in {
      intercept[Exception] {
        resolver.resolveResource("type", "namespace", "publicID", nonExistingFile, "publicURI")
      }
    }
  }
}
