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

import org.w3c.dom.ls.{LSInput, LSResourceResolver}

class ResourceResolver extends LSResourceResolver {

  override def resolveResource(`type`: String, namespaceURI: String, publicId: String, systemId: String, baseURI: String): LSInput = {
    val input = new Input

    input.setSystemId(systemId)
    input.setBaseURI(baseURI)
    input.setPublicId(publicId)

    val resourceAsStream = getClass.getResourceAsStream(s"/xsd/$systemId")

    resourceAsStream match {
      case null => throw new IllegalArgumentException(s"Classpath resource /xsd/$systemId not found")
      case _ =>
        input.setByteStream(resourceAsStream)
        input
    }
  }
}

