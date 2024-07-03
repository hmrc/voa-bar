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

package uk.gov.hmrc.voabar.services

import ebars.xml.BAreports
import jakarta.xml.bind.JAXBContext
import org.w3c.dom.Document
import org.xml.sax.{ErrorHandler, SAXParseException}
import play.api.Logger
import uk.gov.hmrc.voabar.models.{BarError, BarXmlError, BarXmlValidationError, Error}
import uk.gov.hmrc.voabar.util.ErrorCode.INVALID_XML_XSD

import java.io.InputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory
import scala.collection.mutable
import scala.util.{Failure, Success, Try}

class XmlErrorHandler extends ErrorHandler {
  val errors = mutable.Map[Int, Error]()

  private def addError(exception: SAXParseException): Unit = {
    val split = exception.getMessage.split(":", 2) map (_.trim)
    if (split.length == 1) {
      errors.put(-1, Error(INVALID_XML_XSD, Seq(s"Error on line ${exception.getLineNumber}: ${split(0)}")))
    } else {
      errors.put(exception.getLineNumber, Error(INVALID_XML_XSD, Seq(s"Error on line ${exception.getLineNumber}: ${split(1)}")))
    }
  }

  override def warning(exception: SAXParseException): Unit =
    addError(exception)

  override def error(exception: SAXParseException): Unit =
    addError(exception)

  override def fatalError(exception: SAXParseException): Unit =
    addError(exception)
}

class XmlValidator {

  val log = Logger(this.getClass)

  val schemaFile1 = new StreamSource(getClass.getResourceAsStream("/xsd/ValuebillBAtoVOA-v3-1d.xsd"))
  val factory     = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
  factory.setResourceResolver(new ResourceResolver)
  val schema      = factory.newSchema(schemaFile1)

  def validate(document: Document): Either[BarError, Boolean] = {

    val source       = new DOMSource(document)
    val errorHandler = new XmlErrorHandler

    Try {
      val validator = schema.newValidator
      validator.setErrorHandler(errorHandler)
      validator.setFeature("http://xml.org/sax/features/validation", true)
      validator.setFeature("http://apache.org/xml/features/validation/schema", true)
      validator.validate(source)
    } match {
      case Success(_)         =>
        if (errorHandler.errors.isEmpty) {
          Right(true)
        } else {
          Left(BarXmlValidationError(errorHandler.errors.values.toList.distinct))
        }
      case Failure(exception) => Left(BarXmlError("XML Schema validation error"))
    }
  }

  def validateAsDomAgainstSchema(baReports: BAreports): Either[BarError, Unit] = {
    val jc  = JAXBContext.newInstance("ebars.xml")
    val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
    jc.createMarshaller.marshal(baReports, doc)

    validate(doc).map(_ => ())

  }

  /**
   * This method is used ONLY to try parse XML to DOM tree and check if it is well formated XML document.
   * @param xmlInput
   * @return
   */
  def validateInputXmlForXEE(xmlInput: InputStream): Either[BarError, Boolean] = {

    val errorHandler = new XmlErrorHandler

    val maybeInvalid = Try {
      val documentBuilderFactory = DocumentBuilderFactory.newInstance("org.apache.xerces.jaxp.DocumentBuilderFactoryImpl", null)
      documentBuilderFactory.setNamespaceAware(true) // without it fails reconciling the xml with xsd's namespace
      documentBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) // XXE vulnerable fix
      documentBuilderFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false) // XXE vulnerable fix
      documentBuilderFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) // XXE vulnerable fix
      documentBuilderFactory.setFeature("http://xml.org/sax/features/external-general-entities", false) // XXE vulnerable fix
      documentBuilderFactory.setExpandEntityReferences(false) // XXE vulnerable fix

      val parser = documentBuilderFactory.newDocumentBuilder

      parser.setErrorHandler(errorHandler)
      val _ = parser.parse(xmlInput)

    }

    maybeInvalid match {
      case Success(_)         =>
        if (errorHandler.errors.isEmpty) {
          Right(true)
        } else {
          Left(BarXmlValidationError(errorHandler.errors.values.toList.distinct))
        }
      case Failure(exception) =>
        log.warn("XML read error, invalid XML document", exception)
        Left(BarXmlError(s"XML read error, invalid XML document, ${exception.getMessage}"))
    }
  }
}
