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

package uk.gov.hmrc.vo.autobars.util

import java.math.BigInteger
import java.time.{Instant, LocalDate}
import ebars.xml.BApropertySplitMergeStructure.AssessmentProperties
import ebars.xml.BAreportBodyStructure.TypeOfTax.CtaxReasonForReport
import ebars.xml.{BApropertyIdentificationStructure, BApropertySplitMergeStructure, BAreportBodyStructure, BAreports, ContactDetailsStructure, CtaxReasonForReportCodeStructure, EmailStructure, OccupierContactStructure, PersonNameStructure, ReportHeaderStructure, ReportTrailerStructure, TelephoneStructure, TextAddressStructure, UKPostalAddressStructure}
import jakarta.xml.bind.JAXBElement

import javax.xml.datatype.DatatypeFactory
import DateConversion.*
import uk.gov.hmrc.vo.autobars.models.{AddProperty, Cr01Cr03Submission, OtherReason}

import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*

@deprecated("Have bug for CR01, replaced by XmlSubmissionGenerator", "April 2021")
class Cr01Cr03SubmissionXmlGenerator(submission: Cr01Cr03Submission, baCode: Int, baName: String, submissionId: String):

  val OF                        = ebars.xml.ObjectFactory()
  val transactionIdentityLength = 25

  implicit val dataFactory: DatatypeFactory = DatatypeFactory.newInstance()

  def generateXml(): BAreports =
    val report = BAreports()
    report.setBAreportHeader(generateHeader())
    report.setBAreportTrailer(generateReportTrailer())
    report.getBApropertyReport.add(generateBody())
    report.setSchemaId("VbBAtoVOA")
    report.setSchemaVersion("4-0")
    report

  private def generateBody(): BAreportBodyStructure =
    val body = BAreportBodyStructure()

    val bodyElements = ListBuffer(
      OF.createBAreportBodyStructureDateSent(LocalDate.now().toXml),
      OF.createBAreportBodyStructureTransactionIdentityBA(
        submissionId.replaceAll("-", "").substring(0, transactionIdentityLength)
      ), // TODO submissionID
      OF.createBAreportBodyStructureBAidentityNumber(baCode),
      OF.createBAreportBodyStructureBAreportNumber(submission.baReport),
      typeOfTax,
      proposedEntries(),
      OF.createBAreportBodyStructureIndicatedDateOfChange(submission.effectiveDate.toXml)
    )

    if submission.planningRef.isDefined then
      bodyElements += OF.createBAreportBodyStructurePropertyPlanReferenceNumber(submission.planningRef.getOrElse(""))

    if submission.comments.isDefined || submission.noPlanningReference.isDefined || submission.removalReason.isDefined then
      val reasonForRemoval = submission.removalReason.map {
        case OtherReason => submission.otherReason.getOrElse("Unknown reason") // TODO some validation
        case rr          => rr.xmlValue
      }

      bodyElements += OF.createBAreportBodyStructureRemarks(
        List(
          reasonForRemoval,
          submission.noPlanningReference.map(_.xmlValue),
          submission.comments
        ).flatten.mkString(" ")
      )

    body.getContent.addAll(bodyElements.asJavaCollection)
    body

  private def proposedEntries(): JAXBElement[BApropertySplitMergeStructure] =
    val assessmentProperties = AssessmentProperties()
    assessmentProperties.setPropertyIdentity(propertyIdentification())
    assessmentProperties.setOccupierContact(occupierContact())

    val proposed = BApropertySplitMergeStructure()
    proposed.getAssessmentProperties.add(assessmentProperties)

    OF.createBAreportBodyStructureProposedEntries(proposed)

  private def occupierContact(): OccupierContactStructure =
    val person  = PersonNameStructure()
    person.getPersonGivenName.add(submission.propertyContactDetails.firstName)
    person.setPersonFamilyName(submission.propertyContactDetails.lastName)
    val contact = OccupierContactStructure()
    contact.setOccupierName(person)
    if !submission.sameContactAddress then
      submission.contactAddress.foreach { address =>
        val contactAddress = UKPostalAddressStructure()
        contactAddress.getLine.add(address.line1)
        contactAddress.getLine.add(address.line2)
        address.line3.foreach(line3 => contactAddress.getLine.add(line3))
        address.line4.foreach(line4 => contactAddress.getLine.add(line4))
        contactAddress.setPostCode(address.postcode)
        contact.setContactAddress(contactAddress)
      }

    if submission.propertyContactDetails.email.isDefined || submission.propertyContactDetails.phoneNumber.isDefined then
      val nos = ContactDetailsStructure()
      if submission.propertyContactDetails.email.isDefined then
        val email = EmailStructure()
        email.setEmailAddress(submission.propertyContactDetails.email.getOrElse(""))
        nos.getEmail.add(email)

      if submission.propertyContactDetails.phoneNumber.isDefined then
        val tel = TelephoneStructure()
        tel.setTelNationalNumber(submission.propertyContactDetails.phoneNumber.getOrElse(""))
        nos.getTelephone.add(tel)

      contact.setOccupierContactNos(nos)

    contact

  private def propertyIdentification(): BApropertyIdentificationStructure =
    val uprn        = submission.uprn.map { uprn =>
      OF.createUniquePropertyReferenceNumber(uprn.toLong)
    }
    val textAddress = TextAddressStructure()
    textAddress.getAddressLine.add(submission.address.line1)
    textAddress.getAddressLine.add(submission.address.line2)
    submission.address.line3.foreach(line3 => textAddress.getAddressLine.add(line3))
    submission.address.line4.foreach(line4 => textAddress.getAddressLine.add(line4))

    textAddress.setPostcode(submission.address.postcode)
    val jaxbTextAddress = OF.createBApropertyIdentificationStructureTextAddress(textAddress)

    val baReference = OF.createBApropertyIdentificationStructureBAreference(submission.baRef)

    val propertyIdentity = BApropertyIdentificationStructure()
    propertyIdentity.getContent.addAll(List(uprn, Option(jaxbTextAddress), Option(baReference)).flatten.asJava)
    propertyIdentity

  private def typeOfTax =
    val reasonForReportCode                                = CtaxReasonForReportCodeStructure()
    val (reasonForReportValue, reasonForReportDescription) =
      submission.reasonReport.fold(
        (ebars.xml.CtaxReasonForReportCodeContentType.CR_03, AddProperty.reasonForCodeDescription)
      )(rr => (rr.xmlValue, rr.reasonForCodeDescription))
    reasonForReportCode.setValue(reasonForReportValue)

    val cTaxReport = CtaxReasonForReport()
    cTaxReport.setReasonForReportCode(reasonForReportCode)
    cTaxReport.setReasonForReportDescription(reasonForReportDescription)

    val typeOfTax = OF.createBAreportBodyStructureTypeOfTax()
    typeOfTax.setCtaxReasonForReport(cTaxReport)

    OF.createBAreportBodyStructureTypeOfTax(typeOfTax)

  private def generateHeader(): ReportHeaderStructure =
    val header = ReportHeaderStructure()
    header.setBillingAuthority(baName)
    header.setBillingAuthorityIdentityCode(baCode)
    header.setProcessDate(LocalDate.now().toXml)
    header.setEntryDateTime(Instant.now().toXml)
    header

  private def generateReportTrailer(): ReportTrailerStructure =
    val trailer = ReportTrailerStructure()
    trailer.setRecordCount(BigInteger.ONE)
    trailer.setTotalCtaxReportCount(BigInteger.ONE)
    trailer.setTotalNNDRreportCount(BigInteger.ZERO)
    trailer.setEntryDateTime(Instant.now().toXml)
    trailer
