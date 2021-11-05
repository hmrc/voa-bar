/*
 * Copyright 2021 HM Revenue & Customs
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

import java.io.ByteArrayInputStream

import io.inbot.utils.ReplacingInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.scalatestplus.play.PlaySpec

/**
 * This code was used to replicate problem with XML where we have nbsp entity.
 * nbsp is not declared as default XML entity. Also we doesn't permit no-breakable
 * space anywhere in code, so it's better to replace with normal space.
 */
class XmlEntitiesSpec extends PlaySpec {

  "voa-bar" should {
    "parse xml wht nbsp entity" in {
      val xml =
        """<?xml version="1.0" encoding="UTF-8" ?>
          |<root>this is&nbsp;text</root>
          |""".stripMargin.getBytes("UTF-8")


      val documentBuilderFactory = DocumentBuilderFactory.newInstance("org.apache.xerces.jaxp.DocumentBuilderFactoryImpl", null)
      documentBuilderFactory.setNamespaceAware(true)
      documentBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
      documentBuilderFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false)
      documentBuilderFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
      documentBuilderFactory.setFeature("http://xml.org/sax/features/external-general-entities",false)
      documentBuilderFactory.setExpandEntityReferences(false) //XXE vulnerable fix


      val docBuilder = documentBuilderFactory.newDocumentBuilder()

      val doc = docBuilder.parse( new ReplacingInputStream(new ByteArrayInputStream(xml), "&nbsp;", " "))

      doc.getDocumentElement must not be null
      doc.getFirstChild.getFirstChild.getNodeValue mustBe("this is text")

    }
  }
}