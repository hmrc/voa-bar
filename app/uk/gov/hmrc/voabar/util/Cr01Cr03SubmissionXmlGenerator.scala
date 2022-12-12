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

package uk.gov.hmrc.voabar.util

import java.math.BigInteger
import java.time.{Instant, LocalDate}

import ebars.xml.BApropertySplitMergeStructure.AssessmentProperties
import ebars.xml.BAreportBodyStructure.TypeOfTax.CtaxReasonForReport
import ebars.xml.{BApropertyIdentificationStructure, BApropertySplitMergeStructure, BAreportBodyStructure, BAreports,
  ContactDetailsStructure, CtaxReasonForReportCodeStructure, EmailStructure, OccupierContactStructure,
  PersonNameStructure, ReportHeaderStructure, ReportTrailerStructure, TelephoneStructure, TextAddressStructure,
  UKPostalAddressStructure}
import javax.xml.datatype.DatatypeFactory
import uk.gov.hmrc.voabar.models.{AddProperty, Cr01Cr03Submission, OtherReason}
import uk.gov.hmrc.voabar.util.DateConversion._

import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters._

@deprecated("Have bug for CR01, replaced by XmlSubmissionGenerator", "April 2021")
class Cr01Cr03SubmissionXmlGenerator(submission: Cr01Cr03Submission, baCode: Int, baName: String, submissionId: String) {

  val OF = new ebars.xml.ObjectFactory()

  implicit val dataFactory = DatatypeFactory.newInstance()

  def generateXml(): BAreports = {
    val report = new BAreports()
    report.setBAreportHeader(generateHeader())
    report.setBAreportTrailer(generateReportTrailer())
    report.getBApropertyReport.add(generateBody())
    report.setSchemaId("VbBAtoVOA")
    report.setSchemaVersion("4-0")

    report
  }

  def generateBody(): BAreportBodyStructure = {
    val body = new BAreportBodyStructure()

    val bodyElements = ListBuffer(
      OF.createBAreportBodyStructureDateSent(LocalDate.now().toXml),
      OF.createBAreportBodyStructureTransactionIdentityBA(
        submissionId.toString.replaceAll("-", "").substring(0,25)), //TODO submissionID
      OF.createBAreportBodyStructureBAidentityNumber(baCode),
      OF.createBAreportBodyStructureBAreportNumber(submission.baReport),
      typeOfTax,
      proposedEntries(),
      OF.createBAreportBodyStructureIndicatedDateOfChange(submission.effectiveDate.toXml)
    )

    if(submission.planningRef.isDefined) {
      bodyElements += OF.createBAreportBodyStructurePropertyPlanReferenceNumber(submission.planningRef.get)
    }

    if(submission.comments.isDefined || submission.noPlanningReference.isDefined || submission.removalReason.isDefined) {
      val reasonForRemoval = submission.removalReason.map{
        case OtherReason => submission.otherReason.getOrElse("Unknown reason") // TODO some validation
        case rr => rr.xmlValue
      }

      bodyElements += OF.createBAreportBodyStructureRemarks(
        List(
          reasonForRemoval,
          submission.noPlanningReference.map(_.xmlValue),
          submission.comments
        ).flatten.mkString(" ")

      )
    }

    body.getContent.addAll(bodyElements.asJavaCollection)
    body
  }


  def proposedEntries()= {
    val assessmentProperties = new AssessmentProperties()
    assessmentProperties.setPropertyIdentity(propertyIdentification())
    assessmentProperties.setOccupierContact(occupierContact())


    val proposed = new BApropertySplitMergeStructure()
    proposed.getAssessmentProperties.add(assessmentProperties)

    OF.createBAreportBodyStructureProposedEntries(proposed)

  }

  def occupierContact(): OccupierContactStructure = {
    val person = new PersonNameStructure()
    person.getPersonGivenName.add(submission.propertyContactDetails.firstName)
    person.setPersonFamilyName(submission.propertyContactDetails.lastName)
    val contact = new OccupierContactStructure()
    contact.setOccupierName(person)
    if(!submission.sameContactAddress) {
      val address = submission.contactAddress.get
      val contactAddress = new UKPostalAddressStructure()
      contactAddress.getLine.add(address.line1)
      contactAddress.getLine.add(address.line2)
      if(address.line3.isDefined) {
        contactAddress.getLine.add(address.line3.get)
      }
      if(address.line4.isDefined) {
        contactAddress.getLine.add(address.line4.get)
      }
      contactAddress.setPostCode(address.postcode)
      contact.setContactAddress(contactAddress)
    }
    if(submission.propertyContactDetails.email.isDefined || submission.propertyContactDetails.phoneNumber.isDefined) {
      val nos = new ContactDetailsStructure()
      if(submission.propertyContactDetails.email.isDefined) {
        val email = new EmailStructure
        email.setEmailAddress(submission.propertyContactDetails.email.get)
        nos.getEmail.add(email)
      }
      if(submission.propertyContactDetails.phoneNumber.isDefined) {
        val tel = new TelephoneStructure()
        tel.setTelNationalNumber(submission.propertyContactDetails.phoneNumber.get)
        nos.getTelephone.add(tel)
      }
      contact.setOccupierContactNos(nos)
    }


    contact
  }

  def propertyIdentification(): BApropertyIdentificationStructure = {
    val uprn = submission.uprn.map { uprn =>
        OF.createUniquePropertyReferenceNumber(uprn.toLong)
    }
    val textAddress = new TextAddressStructure()
    textAddress.getAddressLine.add(submission.address.line1)
    textAddress.getAddressLine.add(submission.address.line2)
    if(submission.address.line3.isDefined) {
      textAddress.getAddressLine.add(submission.address.line3.get)
    }
    if(submission.address.line4.isDefined) {
      textAddress.getAddressLine.add(submission.address.line4.get)
    }
    textAddress.setPostcode(submission.address.postcode)
    val jaxbTextAddress = OF.createBApropertyIdentificationStructureTextAddress(textAddress)

    val baReference = OF.createBApropertyIdentificationStructureBAreference(submission.baRef)

    val propertyIdentity = new BApropertyIdentificationStructure()
    propertyIdentity.getContent.addAll(List(uprn, Option(jaxbTextAddress), Option(baReference)).flatten.asJava)
    propertyIdentity
  }

  def typeOfTax = {
    val reasonForReportCode = new  CtaxReasonForReportCodeStructure()
    val (reasonForReportValue, reasonForReportDescription) =
      submission.reasonReport.fold(
        (ebars.xml.CtaxReasonForReportCodeContentType.CR_03,
          AddProperty.reasonForCodeDescription))(rr => (rr.xmlValue,rr.reasonForCodeDescription))
    reasonForReportCode.setValue(reasonForReportValue)

    val cTaxReport = new CtaxReasonForReport()
    cTaxReport.setReasonForReportCode(reasonForReportCode)
    cTaxReport.setReasonForReportDescription(reasonForReportDescription)

    val typeOfTax = OF.createBAreportBodyStructureTypeOfTax()
    typeOfTax.setCtaxReasonForReport(cTaxReport)

    OF.createBAreportBodyStructureTypeOfTax(typeOfTax)
  }

  def generateHeader(): ReportHeaderStructure = {
    val header = new ReportHeaderStructure()
    header.setBillingAuthority(baName)
    header.setBillingAuthorityIdentityCode(baCode)
    header.setProcessDate(LocalDate.now().toXml)
    header.setEntryDateTime(Instant.now().toXml)
    header
  }

  def generateReportTrailer():ReportTrailerStructure = {
    val trailer = new ReportTrailerStructure()
    trailer.setRecordCount(BigInteger.ONE)
    trailer.setTotalCtaxReportCount(BigInteger.ONE)
    trailer.setTotalNNDRreportCount(BigInteger.ZERO)
    trailer.setEntryDateTime(Instant.now().toXml)

    trailer
  }

}
