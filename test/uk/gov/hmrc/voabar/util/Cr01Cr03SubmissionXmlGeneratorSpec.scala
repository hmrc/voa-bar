/*
 * Copyright 2026 HM Revenue & Customs
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
import jakarta.xml.bind.{JAXBContext, Marshaller}
import org.scalacheck.Gen
import org.scalacheck.Gen.frequency
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import uk.gov.hmrc.voabar.models.*
import uk.gov.hmrc.voabar.services.{XmlParser, XmlValidator}

import java.io.StringWriter
import java.nio.file.Files
import java.time.LocalDate
import java.util.UUID
import scala.annotation.nowarn

class Cr01Cr03SubmissionXmlGeneratorSpec extends AnyFlatSpec with must.Matchers with EitherValues with ScalaCheckPropertyChecks {

  implicit override val generatorDrivenConfig: PropertyCheckConfiguration = PropertyCheckConfiguration(minSuccessful = 2000)

  private val parser    = new XmlParser()
  private val validator = new XmlValidator()

  private val jaxb           = JAXBContext.newInstance(classOf[BAreports])
  private val jaxbMarshaller = jaxb.createMarshaller()
  jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)

  private def otherChar = Gen.oneOf(""" ~!"@#$%+&;'()*,-./:;<=>?[\]_{}^£€""")

  private def restrictedChar = frequency((1, otherChar), (5, Gen.alphaNumChar))

  private def genRestrictedString(min: Int = 1, max: Int = 8) =
    for
      lenght <- Gen.chooseNum(min, max)
      str    <- Gen.containerOfN[List, Char](lenght, restrictedChar)
    yield str.mkString

  private def genNum(min: Int = 1, max: Int) =
    for
      lenght <- Gen.chooseNum(min, max)
      str    <- Gen.containerOfN[List, Char](lenght, Gen.numChar)
    yield str.mkString

  private def genEffectiveDate =
    for
      year  <- Gen.chooseNum(1993, 2010)
      month <- Gen.chooseNum(1, 12)
      day   <- Gen.chooseNum(1, 28)
    yield LocalDate.of(year, month, day)

  private def genAddress(max: Int = 35) =
    for
      line1 <- genRestrictedString(max = max)
      line2 <- genRestrictedString(max = max)
      line3 <- Gen.option(genRestrictedString(max = max))
      line4 <- Gen.option(genRestrictedString(max = max))
    yield Address(line1, line2, line3, line4, "BN12 4AX")

  private def genContactDetails =
    for
      firstName <- genRestrictedString(max = 35)
      lastName  <- genRestrictedString(max = 35)
      email     <- Gen.option(genRestrictedString())
      phone     <- Gen.option(genNum(max = 20))
    yield ContactDetails(firstName, lastName, email, phone)

  private def genPlanningReference: Gen[(Option[String], Option[NoPlanningReferenceType])] =
    for
      planningRef   <- Gen.option(genRestrictedString(max = 25))
      noPlanningRef <-
        Gen.oneOf(
          WithoutPlanningPermission,
          NotApplicablePlanningPermission,
          NotRequiredPlanningPermission,
          PermittedDevelopment,
          NoPlanningApplicationSubmitted
        )
    yield planningRef.fold((None, Some(noPlanningRef)))(_ => (planningRef, None))

  private def getCr03Submission =
    for
      reportReason                 <- Gen.option(AddProperty)
      baReport                     <- genRestrictedString(max = 12)
      baRef                        <- genRestrictedString(max = 25)
      uprn                         <- Gen.option(Gen.chooseNum(1L, 999999999999L).map(_.toString))
      address                      <- genAddress(max = 100)
      contactAddress               <- Gen.option(genAddress())
      contactDetails               <- genContactDetails
      effectiveDate                <- genEffectiveDate
      (planningRef, noPlanningRef) <- genPlanningReference
      comment                      <- Gen.option(genRestrictedString(max = 226))
    yield Cr01Cr03Submission(
      reportReason,
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

  private def getCr01Submission =
    for
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
      otherReason                  <- if removalReason.contains(OtherReason) then Gen.some(genRestrictedString(max = 32)) else Gen.const(Option.empty[String])
      baReport                     <- genRestrictedString(max = 12)
      baRef                        <- genRestrictedString(max = 25)
      uprn                         <- Gen.option(Gen.chooseNum(1L, 999999999999L).map(_.toString))
      address                      <- genAddress(max = 100)
      contactAddress               <- Gen.some(genAddress())
      contactDetails               <- genContactDetails
      effectiveDate                <- genEffectiveDate
      (planningRef, noPlanningRef) <- genPlanningReference
      comment                      <- Gen.option(genRestrictedString(max = 150))
    yield Cr01Cr03Submission(
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

  "CR01 CR03 generator" should "generate valid xml" in {
    val jaxbStructure =
      new Cr01Cr03SubmissionXmlGenerator(
        aCR03Submission(),
        1010,
        "Brighton and Hove",
        UUID.randomUUID().toString
      ).generateXml(): @nowarn
    val xml           = printXml(jaxbStructure)

    validateXml(xml)

    true must be(true)
  }

  it should "generate valid XML for all generated CR03 submissions" in {
    val id = UUID.randomUUID().toString
    forAll(getCr03Submission) { submission =>
      val jaxbStructure =
        new Cr01Cr03SubmissionXmlGenerator(
          submission,
          1010,
          "Brighton and Hove",
          id
        ).generateXml(): @nowarn
      val xml           = printXml(jaxbStructure)

      validateXml(xml)

      true must be(true)
    }
  }

  it should "generate valid XML for all generated CR01 submissions" in {
    val id = UUID.randomUUID().toString
    forAll(getCr01Submission) { submission =>
      val jaxbStructure =
        new Cr01Cr03SubmissionXmlGenerator(
          submission,
          1010,
          "Brighton and Hove",
          id
        ).generateXml(): @nowarn
      val xml           = printXml(jaxbStructure)

      validateXml(xml)

      true must be(true)
    }
  }

  def validateXml(xml: String): Unit = {
    val file = Files.createTempFile("test-xml", ".xml")
    Files.write(file, xml.getBytes("UTF-8"))

    val validation = parser.parse(file.toUri.toURL)
      .flatMap(validator.validate)

    if validation.isLeft then println(s"\n\n\n${validation.left}\n\n$xml")

    validation mustBe Right(true)
  }

  def printXml(report: BAreports): String = {
    val sw = new StringWriter()
    jaxbMarshaller.marshal(report, sw)
    sw.toString
  }

  def aCR03Submission(): Cr01Cr03Submission = {
    val address        = Address("line 1 ]]>", "line2", Option("line3"), None, "BN12 4AX")
    val contactDetails = ContactDetails("John", "Doe", Option("john.doe@example.com"), Option("054252365447"))
    Cr01Cr03Submission(
      None,
      None,
      None,
      "baReport",
      "baRef",
      Option("112541"),
      address,
      contactDetails,
      true,
      None,
      LocalDate.now(),
      true,
      Some("22212"),
      None,
      Option("comment")
    )
  }

}
