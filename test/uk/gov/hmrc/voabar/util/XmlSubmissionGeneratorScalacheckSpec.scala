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
import org.scalacheck.Gen
import org.scalacheck.Gen.frequency
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.EitherValues
import org.scalatest.matchers.must
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import org.xmlunit.builder.DiffBuilder
import org.xmlunit.diff.{Comparison, ComparisonResult, DifferenceEvaluator}
import uk.gov.hmrc.voabar.models._
import uk.gov.hmrc.voabar.services.{XmlParser, XmlValidator}

import java.io.StringWriter
import java.nio.file.Files
import java.time.LocalDate
import java.util.UUID
import jakarta.xml.bind.{JAXBContext, Marshaller}

import scala.annotation.nowarn
import scala.jdk.CollectionConverters._

class XmlSubmissionGeneratorScalacheckSpec extends AnyFlatSpec with must.Matchers with EitherValues with ScalaCheckPropertyChecks {

  implicit override val generatorDrivenConfig: PropertyCheckConfiguration = PropertyCheckConfiguration(minSuccessful = 500)

  val parser    = new XmlParser()
  val validator = new XmlValidator()

  val jaxb           = JAXBContext.newInstance(classOf[BAreports])
  val jaxbMarshaller = jaxb.createMarshaller()
  jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)

  def otherChar = Gen.oneOf(""" ~!"@#$%+&;'()*,-./:;<=>?[\]_{}^£€""".toSeq)

  def restrictedChar = frequency((1, otherChar), (5, Gen.alphaNumChar))

  def genRestrictedString(min: Int = 1, max: Int = 8) = for {
    lenght <- Gen.chooseNum(min, max)
    str    <- Gen.containerOfN[List, Char](lenght, restrictedChar)
  } yield str.mkString

  def genNum(min: Int = 1, max: Int = 8) = for {
    lenght <- Gen.chooseNum(min, max)
    str    <- Gen.containerOfN[List, Char](lenght, Gen.numChar)
  } yield str.mkString

  def genEffectiveDate = for {
    year  <- Gen.chooseNum(1993, 2010)
    month <- Gen.chooseNum(1, 12)
    day   <- Gen.chooseNum(1, 28)
  } yield LocalDate.of(year, month, day)

  def genAddress(max: Int = 35) = for {
    line1 <- genRestrictedString(max = max)
    line2 <- genRestrictedString(max = max)
    line3 <- Gen.option(genRestrictedString(max = max))
    line4 <- Gen.option(genRestrictedString(max = max))
  } yield Address(line1, line2, line3, line4, "BN12 4AX")

  def genContactDetails = for {
    firstName <- genRestrictedString(max = 35)
    lastName  <- genRestrictedString(max = 35)
    email     <- Gen.option(genRestrictedString())
    phone     <- Gen.option(genNum(max = 20))
  } yield ContactDetails(firstName, lastName, email, phone)

  def genPlanningReference = for {
    planningRef   <- Gen.option(genRestrictedString(max = 25))
    noPlanningRef <-
      Gen.oneOf(WithoutPlanningPermission, NotApplicablePlanningPermission, NotRequiredPlanningPermission, PermittedDevelopment, NoPlanningApplicationSubmitted)
  } yield
    if (planningRef.isDefined) {
      (planningRef, None)
    } else {
      (planningRef, Some(noPlanningRef))
    }

  def genCr03Submission =
    for {
      // reportReason <- Gen.option(AddProperty)
      baReport                     <- genRestrictedString(max = 12)
      baRef                        <- genRestrictedString(max = 25)
      uprn                         <- Gen.option(Gen.chooseNum(1L, 999999999999L).map(_.toString))
      address                      <- genAddress(max = 100)
      contactAddress               <- Gen.option(genAddress())
      contactDetails               <- genContactDetails
      effectiveDate                <- genEffectiveDate
      (planningRef, noPlanningRef) <- genPlanningReference
      comment                      <- Gen.option(genRestrictedString(max = 226))
    } yield Cr01Cr03Submission(
      Some(AddProperty),
      None,
      None,
      baReport,
      baRef,
      uprn,
      address,
      contactDetails,
      contactAddress.isEmpty,
      contactAddress,
      effectiveDate,
      planningRef.isDefined,
      planningRef,
      noPlanningRef,
      comment
    )

  def genCr01Submission =
    for {
      reportReason                 <- Gen.some(RemoveProperty)
      removalReason                <- Gen.option(Gen.oneOf[RemovalReasonType](
                                        Demolition,
                                        Disrepair,
                                        Renovating,
                                        NotComplete,
                                        BandedTooSoon,
                                        CaravanRemoved,
                                        Duplicate,
                                        OtherReason
                                      ))
      otherReason                  <- if (removalReason.contains(OtherReason))
                                        Gen.some(genRestrictedString(max = 32))
                                      else Gen.const(Option.empty[String])
      baReport                     <- genRestrictedString(max = 12)
      baRef                        <- genRestrictedString(max = 25)
      uprn                         <- Gen.option(Gen.chooseNum(1L, 999999999999L).map(_.toString))
      address                      <- genAddress(max = 100)
      contactAddress               <- Gen.some(genAddress())
      contactDetails               <- genContactDetails
      effectiveDate                <- genEffectiveDate
      (planningRef, noPlanningRef) <- genPlanningReference
      comment                      <- Gen.option(genRestrictedString(max = 150))
    } yield Cr01Cr03Submission(
      reportReason,
      removalReason,
      otherReason,
      baReport,
      baRef,
      uprn,
      address,
      contactDetails,
      contactAddress.isEmpty,
      contactAddress,
      effectiveDate,
      planningRef.isDefined,
      planningRef,
      noPlanningRef,
      comment
    )

  def genProperty = for {
    uprn                   <- Gen.option(Gen.chooseNum(1L, 999999999999L).map(_.toString))
    address                <- genAddress()
    propertyContactDetails <- genContactDetails
    contactAddress         <- Gen.option(genAddress())
  } yield Cr05AddProperty(uprn, address, propertyContactDetails, contactAddress.isEmpty, contactAddress)

  def genProperties = for {
    numberOfProperties <- Gen.chooseNum(1, 5)
    properties         <- Gen.containerOfN[List, Cr05AddProperty](numberOfProperties, genProperty)
  } yield properties

  def genCr05Submission =
    for {
      baReport                     <- genRestrictedString(max = 12)
      baRef                        <- genRestrictedString(max = 25)
      effectiveDate                <- genEffectiveDate
      commentValue                 <- Gen.option(genRestrictedString(max = 150))
      proposedProperties           <- genProperties
      existingPropertis            <- genProperties
      (planningRef, noPlanningRef) <- genPlanningReference
    } yield Cr05Submission(
      baReport = baReport,
      baRef = baRef,
      effectiveDate = effectiveDate,
      proposedProperties = proposedProperties,
      existingPropertis = existingPropertis,
      planningRef = planningRef,
      noPlanningReference = noPlanningRef,
      comments = commentValue
    )

  "XmlSubmissionGenerator" should "generate valid XML for all generated CR03 submissions" in {
    val id = UUID.randomUUID().toString
    forAll(genCr03Submission) { submission =>
      val jaxbStructure =
        new XmlSubmissionGenerator(
          submission,
          1010,
          "Brighton and Hove",
          id
        ).generateXml()
      val xml           = printXml(jaxbStructure)

      validateXml(xml)

      true must be(true)
    }
  }

  "XmlSubmissionGenerator" should "generate valid XML for all generated CR01 submissions" in {
    val id = UUID.randomUUID().toString
    forAll(genCr01Submission) { submission =>
      val jaxbStructure =
        new XmlSubmissionGenerator(
          submission,
          1010,
          "Brighton and Hove",
          id
        ).generateXml()
      val xml           = printXml(jaxbStructure)

      validateXml(xml)

      true must be(true)
    }
  }

  "XmlSubmissionGenerator" should "generate valid XML for all generated CR05 submissions" in {
    val id = UUID.randomUUID().toString
    forAll(genCr05Submission) { submission =>
      val jaxbStructure =
        new XmlSubmissionGenerator(
          submission,
          1010,
          "Brighton and Hove",
          id
        ).generateXml()
      val xml           = printXml(jaxbStructure)

      validateXml(xml)

      true must be(true)
    }
  }

  // This code can be removed after Cr01Cr03SubmissionXmlGenerator is removed
  "XmlSubmissionGenerator" should "generate generate same valid XML for both implementation for CR03" in {
    val id = UUID.randomUUID().toString
    forAll(genCr03Submission) { submission =>
      val jaxbStructure =
        new XmlSubmissionGenerator(
          submission,
          1010,
          "Brighton and Hove",
          id
        ).generateXml()
      val xml           = printXml(jaxbStructure)
      validateXml(xml)

      val oldXmlJaxbStructure = new Cr01Cr03SubmissionXmlGenerator(submission, 1010, "Brighton and Hove", id).generateXml(): @nowarn
      val oldXml              = printXml(oldXmlJaxbStructure)
      validateXml(oldXml)

      import org.xmlunit.builder.Input
      val diff = DiffBuilder.compare(Input.fromString(xml))
        .withTest(Input.fromString(oldXml))
        .withDifferenceEvaluator(evaluator).build()

      diff.hasDifferences mustBe false
    }
  }

  // This code can be removed after Cr01Cr03SubmissionXmlGenerator is removed
  // Cr01Cr03SubmissionXmlGenerator have for CR01 and doesn't use proposed entries.
  "XmlSubmissionGenerator" should "generate generate different valid XML for both implementation for CR01" in {
    val id = UUID.randomUUID().toString
    forAll(genCr01Submission) { submission =>
      val jaxbStructure =
        new XmlSubmissionGenerator(
          submission,
          1010,
          "Brighton and Hove",
          id
        ).generateXml()
      val xml           = printXml(jaxbStructure)
      validateXml(xml)

      val oldXmlJaxbStructure = new Cr01Cr03SubmissionXmlGenerator(submission, 1010, "Brighton and Hove", id).generateXml(): @nowarn
      val oldXml              = printXml(oldXmlJaxbStructure)
      validateXml(oldXml)

      import org.xmlunit.builder.Input
      val diff = DiffBuilder.compare(Input.fromString(xml))
        .withTest(Input.fromString(oldXml))
        .withDifferenceEvaluator(evaluator).build()

      diff.hasDifferences mustBe true

      val differences = diff.getDifferences.asScala.toList
      differences must have length 1
      differences(0).getComparison.getControlDetails.getTarget.getNodeName mustBe "ExistingEntries"

    }
  }

  val evaluator =
    new DifferenceEvaluator {
      override def evaluate(comparison: Comparison, outcome: ComparisonResult): ComparisonResult =
        if (outcome != ComparisonResult.EQUAL) {
          if (comparison.getControlDetails.getTarget.getParentNode.getNodeName == "EntryDateTime") {
            ComparisonResult.EQUAL
          } else {
            outcome
          }
        } else {
          outcome
        }

    }

  def printXml(report: BAreports): String = {
    val sw = new StringWriter()
    jaxbMarshaller.marshal(report, sw)
    sw.toString
  }

  def validateXml(xml: String): Unit = {
    val file       = Files.createTempFile("test-xml", ".xml")
    Files.write(file, xml.getBytes("UTF-8"))
    val dom        = parser.parse(file.toUri.toURL).toOption.get
    val validation = validator.validate(dom)

    if (validation.isLeft) {
      Console.println(s"\n\n\n${validation.left}\n\n$xml")
    }

    validation.value mustBe true
  }

}
