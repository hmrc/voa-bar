/*
 * Copyright 2022 HM Revenue & Customs
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

import java.io.ByteArrayInputStream
import java.net.URL

import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.sax.SAXResult
import org.w3c.dom.Document
import uk.gov.hmrc.voabar.models.{BarError, BarXmlError}

import scala.util.{Failure, Success, Try}
import scala.xml._
import scala.xml.parsing.NoBindingFactoryAdapter

class XmlParser {

  val documentBuilderFactory = DocumentBuilderFactory.newInstance("org.apache.xerces.jaxp.DocumentBuilderFactoryImpl", null)
  documentBuilderFactory.setNamespaceAware(true)
  documentBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
  documentBuilderFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false)
  documentBuilderFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
  documentBuilderFactory.setFeature("http://xml.org/sax/features/external-general-entities",false)
  documentBuilderFactory.setExpandEntityReferences(false) //XXE vulnerable fix


  def parse(xml: URL): Either[BarError, Document] = {
    import scala.concurrent.blocking

    Try {
      val docBuilder = documentBuilderFactory.newDocumentBuilder()
      blocking { //downloading and parsing XML is blocking operation, maybe we can buffer it to byte[] and then parse to avoid blocking IO
        docBuilder.parse(xml.openStream())
      }
    } match {
      case Success(value) => Right(value)
      case Failure(x) => Left(BarXmlError(x.getMessage))
    }
  }

  def parse(xml: Array[Byte]): Either[BarError, Document] = {

    Try {
      val docBuilder = documentBuilderFactory.newDocumentBuilder()
      docBuilder.parse(new ByteArrayInputStream(xml))
    } match {
      case Success(value) => Right(value)
      case Failure(x) => Left(BarXmlError(x.getMessage))
    }
  }


  private def addChild(node:Node,newNode:NodeSeq): Node = (node: @unchecked) match {
    case Elem(prefix,label,attrs,ns,child@_*) => Elem(prefix,label,attrs,ns,false,newNode: _*)
  }

  def oneReportPerBatch(node:Node):Seq[Node] = {
    val batchHeader = node \ "BAreportHeader"
    val batchTrailer = node \ "BAreportTrailer"
    (node \ "BApropertyReport") map {report => addChild(node,batchHeader ++ report ++ batchTrailer)}
  }

  /*

https://martin.elwin.com/blog/2008/05/scala-xml-and-java-dom/

  Using Char array
//dom is the DOM Element
val charWriter = new CharArrayWriter()
TransformerFactory.newInstance.newTransformer.transform(new DOMSource(dom), new StreamResult(charWriter))
val xml = XML.load(new CharArrayReader(charWriter.toCharArray))

  TODO - test properly with namespace, scala XML libraries handle namespace incorectly!!!
   */
  def domToScala(document: Document): Either[BarError, Node] = {
    Try {
      val saxHandler = new NoBindingFactoryAdapter()
      TransformerFactory
        .newInstance("net.sf.saxon.TransformerFactoryImpl", null)
        .newTransformer
        .transform(new DOMSource(document), new SAXResult(saxHandler))

      saxHandler.rootElem
    } match {
      case Success(scalaNode) => Right(scalaNode)
      case Failure(exception) => Left(BarXmlError(exception.getMessage))
    }
  }

}
