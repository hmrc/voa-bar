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

package uk.gov.hmrc.voabar.util

import ebars.xml.BAreports
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import org.xmlunit.builder.Input
import org.xmlunit.xpath.JAXPXPathEngine
import uk.gov.hmrc.voabar.models.{AddProperty, Address, ContactDetails, Cr01Cr03Submission, Cr05AddProperty, Cr05Submission, Demolition, RemoveProperty}
import uk.gov.hmrc.voabar.services.{XmlParser, XmlValidator}

import java.io.StringWriter
import java.nio.file.Files
import java.time.LocalDate
import java.util.UUID
import jakarta.xml.bind.{JAXBContext, Marshaller}
import scala.jdk.CollectionConverters._

class XmlSubmissionGeneratorSpec extends AnyFlatSpec with must.Matchers with EitherValues with ScalaCheckPropertyChecks {

  implicit override val generatorDrivenConfig: PropertyCheckConfiguration = PropertyCheckConfiguration(minSuccessful = 2000)

  val parser    = new XmlParser()
  val validator = new XmlValidator()

  val jaxb           = JAXBContext.newInstance(classOf[BAreports])
  val jaxbMarshaller = jaxb.createMarshaller()
  jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)

  "submission generator" should "generate CR05 XML" in {
    val submission = aCr05Submission

    val baReport = new XmlSubmissionGenerator(submission, 934, "Hogwarts", UUID.randomUUID().toString).generateXml()

    val sw = new StringWriter()
    jaxbMarshaller.marshal(baReport, sw)

    validateXml(sw.toString)

    val source = Input.fromString(sw.toString).build()

    val xPath = new JAXPXPathEngine()
    // XML is produce with namespace, we MUST define namespace and used them, otherwise it doesn't work
    xPath.setNamespaceContext(Map(
      "ba"     -> "http://www.govtalk.gov.uk/LG/Valuebill",
      "bs7666" -> "http://www.govtalk.gov.uk/people/bs7666",
      "apd"    -> "http://www.govtalk.gov.uk/people/AddressAndPersonalDetails",
      "ns2"    -> "http://www.govtalk.gov.uk/people/PersonDescriptives"
    ).asJava)

    xPath.evaluate(
      "/ba:BAreports/ba:BApropertyReport/ba:ExistingEntries/ba:AssessmentProperties[1]/ba:PropertyIdentity/ba:TextAddress/ba:AddressLine[1]/text()",
      source
    ) mustBe "ex1 line 1"

    xPath.evaluate("/ba:BAreports/ba:BApropertyReport/ba:TypeOfTax/ba:CtaxReasonForReport/ba:ReasonForReportCode", source) mustBe "CR05"

    xPath.evaluate("count(/ba:BAreports/ba:BApropertyReport/ba:ExistingEntries/ba:AssessmentProperties)", source) mustBe "2"

    xPath.evaluate("count(/ba:BAreports/ba:BApropertyReport/ba:ProposedEntries/ba:AssessmentProperties)", source) mustBe "2"

    xPath.evaluate("/ba:BAreports/ba:BApropertyReport/ba:PropertyPlanReferenceNumber", source) mustBe "1234"

  }

  it should "generate CR01 XML" in {
    val submission = aCr01Submission

    val baReport = new XmlSubmissionGenerator(submission, 934, "Hogwarts", UUID.randomUUID().toString).generateXml()

    val sw = new StringWriter()
    jaxbMarshaller.marshal(baReport, sw)

    validateXml(sw.toString)

    val source = Input.fromString(sw.toString).build()

    val xPath = new JAXPXPathEngine()
    // XML is produce with namespace, we MUST define namespace and used them, otherwise it doesn't work
    xPath.setNamespaceContext(Map(
      "ba"     -> "http://www.govtalk.gov.uk/LG/Valuebill",
      "bs7666" -> "http://www.govtalk.gov.uk/people/bs7666",
      "apd"    -> "http://www.govtalk.gov.uk/people/AddressAndPersonalDetails",
      "ns2"    -> "http://www.govtalk.gov.uk/people/PersonDescriptives"
    ).asJava)

    xPath.evaluate(
      "/ba:BAreports/ba:BApropertyReport/ba:ExistingEntries/ba:AssessmentProperties[1]/ba:PropertyIdentity/ba:TextAddress/ba:AddressLine[1]/text()",
      source
    ) mustBe "line 1"

    xPath.evaluate("/ba:BAreports/ba:BApropertyReport/ba:TypeOfTax/ba:CtaxReasonForReport/ba:ReasonForReportCode", source) mustBe "CR01"

    xPath.evaluate("count(/ba:BAreports/ba:BApropertyReport/ba:ExistingEntries/ba:AssessmentProperties)", source) mustBe "1"

    xPath.evaluate("count(/ba:BAreports/ba:BApropertyReport/ba:ProposedEntries/ba:AssessmentProperties)", source) mustBe "0"

  }

  it should "generate CR03 XML" in {
    val submission = aCr03Submission

    val baReport = new XmlSubmissionGenerator(submission, 934, "Hogwarts", UUID.randomUUID().toString).generateXml()

    val sw = new StringWriter()
    jaxbMarshaller.marshal(baReport, sw)

    validateXml(sw.toString)

    val source = Input.fromString(sw.toString).build()

    val xPath = new JAXPXPathEngine()
    // XML is produce with namespace, we MUST define namespace and used them, otherwise it doesn't work
    xPath.setNamespaceContext(Map(
      "ba"     -> "http://www.govtalk.gov.uk/LG/Valuebill",
      "bs7666" -> "http://www.govtalk.gov.uk/people/bs7666",
      "apd"    -> "http://www.govtalk.gov.uk/people/AddressAndPersonalDetails",
      "ns2"    -> "http://www.govtalk.gov.uk/people/PersonDescriptives"
    ).asJava)

    xPath.evaluate(
      "/ba:BAreports/ba:BApropertyReport/ba:ProposedEntries/ba:AssessmentProperties[1]/ba:PropertyIdentity/ba:TextAddress/ba:AddressLine[1]/text()",
      source
    ) mustBe "line 1"

    xPath.evaluate("/ba:BAreports/ba:BApropertyReport/ba:TypeOfTax/ba:CtaxReasonForReport/ba:ReasonForReportCode", source) mustBe "CR03"

    xPath.evaluate("count(/ba:BAreports/ba:BApropertyReport/ba:ProposedEntries/ba:AssessmentProperties)", source) mustBe "1"

    xPath.evaluate("count(/ba:BAreports/ba:BApropertyReport/ba:ExistingEntries/ba:AssessmentProperties)", source) mustBe "0"

  }

  def aCr03Submission: Cr01Cr03Submission =
    Cr01Cr03Submission(
      baReport = "baReport",
      baRef = "baRef",
      effectiveDate = LocalDate.of(2020, 2, 2),
      reasonReport = Option(AddProperty),
      removalReason = None,
      otherReason = None,
      uprn = Option("123123"),
      address = Address("line 1", "line 2", None, None, "BN12 4AX"),
      propertyContactDetails = ContactDetails("firstName", "lastName", Option("john@example.com"), Option("0125458545")),
      sameContactAddress = true,
      contactAddress = None,
      havePlaningReference = true,
      planningRef = Option("planning ref"),
      noPlanningReference = None,
      comments = Option("comment")
    )

  def aCr01Submission: Cr01Cr03Submission =
    Cr01Cr03Submission(
      baReport = "baReport",
      baRef = "baRef",
      effectiveDate = LocalDate.of(2020, 2, 2),
      reasonReport = Option(RemoveProperty),
      removalReason = Option(Demolition),
      otherReason = None,
      uprn = Option("123123"),
      address = Address("line 1", "line 2", None, None, "BN12 4AX"),
      propertyContactDetails = ContactDetails("firstName", "lastName", Option("john@example.com"), Option("0125458545")),
      sameContactAddress = true,
      contactAddress = None,
      havePlaningReference = true,
      planningRef = Option("planning ref"),
      noPlanningReference = None,
      comments = Option("comment")
    )

  def aCr05Submission: Cr05Submission =
    Cr05Submission(
      baReport = "baReport",
      baRef = "baRef",
      effectiveDate = LocalDate.of(2020, 2, 2),
      proposedProperties = Seq(aProperty("prop1"), aProperty("prop2")),
      existingPropertis = Seq(aProperty("ex1"), aProperty("ex2")),
      planningRef = Option("1234"),
      None,
      comments = Option("comments")
    )

  def aProperty(prefix: String): Cr05AddProperty =
    Cr05AddProperty(
      uprn = Option("112331"),
      address = Address(s"$prefix line 1", s"$prefix line 2", None, None, "BN12 4AX"),
      propertyContactDetails = ContactDetails(s"$prefix firstName", s"$prefix lastName", Option("john@example.com"), Option("0125458545")),
      sameContactAddress = false,
      contactAddress = Some(Address(s"$prefix line 1", s"$prefix line 2", None, None, "BN12 4AX"))
    )

  def validateXml(xml: String): Unit = {
    val file = Files.createTempFile("test-xml", ".xml")
    Files.write(file, xml.getBytes("UTF-8"))

    val validation = parser.parse(file.toUri.toURL)
      .flatMap(validator.validate)

    if validation.isLeft then println(s"\n\n\n${validation.left}\n\n$xml")

    validation mustBe Right(true)
  }

}
