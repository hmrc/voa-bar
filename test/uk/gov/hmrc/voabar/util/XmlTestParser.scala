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

package uk.gov.hmrc.voabar.util

import org.apache.commons.io.input.ReaderInputStream
import org.w3c.dom.Document
import uk.gov.hmrc.voabar.services.XmlParser

import java.io.StringReader
import java.nio.charset.StandardCharsets.UTF_8

object XmlTestParser {

  def parseXml(xml: String): Document = {
    val xmlParser = new XmlParser()

    val docBuilder = xmlParser.documentBuilderFactory.newDocumentBuilder()
    docBuilder.parse(ReaderInputStream.builder()
      .setReader(new StringReader(xml))
      .setCharset(UTF_8)
      .get()
    )


  }


}
