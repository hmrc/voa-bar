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

import ebars.xml.BApropertySplitMergeStructure.AssessmentProperties
import ebars.xml.BAreportBodyStructure.TypeOfTax.CtaxReasonForReport
import ebars.xml._
import uk.gov.hmrc.voabar.models._
import uk.gov.hmrc.voabar.util.DateConversion._

import java.math.BigInteger
import java.time.{Instant, LocalDate}
import jakarta.xml.bind.JAXBElement
import javax.xml.datatype.DatatypeFactory
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters._

class XmlSubmissionGenerator(submission: CrSubmission, baCode: Int, baName: String, submissionId: String) {

  val OF                        = new ebars.xml.ObjectFactory()
  val transactionIdentityLength = 25

  implicit val dataFactory: DatatypeFactory = DatatypeFactory.newInstance()

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
    val bodyElements: ListBuffer[JAXBElement[_]] = ListBuffer(
      OF.createBAreportBodyStructureDateSent(LocalDate.now().toXml),
      OF.createBAreportBodyStructureTransactionIdentityBA(submissionId.replaceAll("-", "").substring(0, transactionIdentityLength)), // TODO submissionID
      OF.createBAreportBodyStructureBAidentityNumber(baCode),
      OF.createBAreportBodyStructureBAreportNumber(submission.baReport),
      typeOfTax
    )

    submission match {
      case submission: Cr01Cr03Submission =>
        bodyElements += cr01Cr03PropertyEntries()
        bodyElements += OF.createBAreportBodyStructureIndicatedDateOfChange(submission.effectiveDate.toXml)

        if (submission.planningRef.isDefined) {
          bodyElements += OF.createBAreportBodyStructurePropertyPlanReferenceNumber(submission.planningRef.get)
        }

        if (submission.comments.isDefined || submission.noPlanningReference.isDefined || submission.removalReason.isDefined) {
          bodyElements += OF.createBAreportBodyStructureRemarks(
            List(reasonForRemoval(submission), submission.noPlanningReference.map(_.xmlValue), submission.comments).flatten.mkString(" ")
          )
        }
      case submission: Cr05Submission     =>
        bodyElements += OF.createBAreportBodyStructureExistingEntries(createProperties(submission.existingPropertis))
        bodyElements += OF.createBAreportBodyStructureProposedEntries(createProperties(submission.proposedProperties))
        bodyElements += OF.createBAreportBodyStructureIndicatedDateOfChange(submission.effectiveDate.toXml)

        if (submission.planningRef.isDefined) {
          bodyElements += OF.createBAreportBodyStructurePropertyPlanReferenceNumber(submission.planningRef.get)
        }

        if (submission.comments.isDefined || submission.noPlanningReference.isDefined) {
          bodyElements += OF.createBAreportBodyStructureRemarks(
            List(submission.noPlanningReference.map(_.xmlValue), submission.comments).flatten.mkString(" ")
          )
        }
    }

    val body = new BAreportBodyStructure()
    body.getContent.addAll(bodyElements.asJavaCollection)
    body
  }

  private def reasonForRemoval(submission: Cr01Cr03Submission) =
    submission.removalReason.map {
      case OtherReason => submission.otherReason.getOrElse("Unknown reason") // TODO some validation
      case rr          => rr.xmlValue
    }

  def createProperties(properties: Seq[Cr05AddProperty]) = {

    val reportProperties = properties.map { property =>
      val assessmentProperties = new AssessmentProperties()
      assessmentProperties.setPropertyIdentity(propertyIdentification(property.uprn, property.address))
      assessmentProperties.setOccupierContact(occupierContact(property.propertyContactDetails, property.propertyContactDetails, property.contactAddress))
      assessmentProperties
    }

    val entries = new BApropertySplitMergeStructure()
    entries.getAssessmentProperties.addAll(reportProperties.asJavaCollection)
    entries
  }

  def cr01Cr03PropertyEntries() = {
    val sub = submission.asInstanceOf[Cr01Cr03Submission]

    val assessmentProperties = new AssessmentProperties()
    assessmentProperties.setPropertyIdentity(propertyIdentification(sub.uprn, sub.address))
    assessmentProperties.setOccupierContact(occupierContact(sub.propertyContactDetails, sub.propertyContactDetails, sub.contactAddress))

    val entries = new BApropertySplitMergeStructure()
    entries.getAssessmentProperties.add(assessmentProperties)

    sub.reasonReport match {
      case Some(AddProperty)    => OF.createBAreportBodyStructureProposedEntries(entries)
      case Some(RemoveProperty) => OF.createBAreportBodyStructureExistingEntries(entries)
      case x                    => throw new RuntimeException(s"Unknown CR01 or CR03 reason type $x")
    }

  }

  def occupierContact(contactDetail: ContactDetails, propertyContactDetails: ContactDetails, maybeContactAddress: Option[Address]): OccupierContactStructure = {
    val person  = new PersonNameStructure()
    person.getPersonGivenName.add(contactDetail.firstName)
    person.setPersonFamilyName(contactDetail.lastName)
    val contact = new OccupierContactStructure()
    contact.setOccupierName(person)

    maybeContactAddress.foreach { address =>
      val contactAddress = new UKPostalAddressStructure()
      contactAddress.getLine.add(address.line1)
      contactAddress.getLine.add(address.line2)
      if (address.line3.isDefined) {
        contactAddress.getLine.add(address.line3.get)
      }
      if (address.line4.isDefined) {
        contactAddress.getLine.add(address.line4.get)
      }
      contactAddress.setPostCode(address.postcode)
      contact.setContactAddress(contactAddress)
    }

    if (propertyContactDetails.email.isDefined || propertyContactDetails.phoneNumber.isDefined) {
      val nos = new ContactDetailsStructure()
      if (propertyContactDetails.email.isDefined) {
        val email = new EmailStructure
        email.setEmailAddress(propertyContactDetails.email.get)
        nos.getEmail.add(email)
      }
      if (propertyContactDetails.phoneNumber.isDefined) {
        val tel = new TelephoneStructure()
        tel.setTelNationalNumber(propertyContactDetails.phoneNumber.get)
        nos.getTelephone.add(tel)
      }
      contact.setOccupierContactNos(nos)
    }

    contact
  }

  def propertyIdentification(maybeUprn: Option[String], address: Address): BApropertyIdentificationStructure = {
    val uprn            = maybeUprn.map { uprn =>
      OF.createUniquePropertyReferenceNumber(uprn.toLong)
    }
    val textAddress     = new TextAddressStructure()
    textAddress.getAddressLine.add(address.line1)
    textAddress.getAddressLine.add(address.line2)
    if (address.line3.isDefined) {
      textAddress.getAddressLine.add(address.line3.get)
    }
    if (address.line4.isDefined) {
      textAddress.getAddressLine.add(address.line4.get)
    }
    textAddress.setPostcode(address.postcode)
    val jaxbTextAddress = OF.createBApropertyIdentificationStructureTextAddress(textAddress)

    val baReference = OF.createBApropertyIdentificationStructureBAreference(submission.baRef)

    val propertyIdentity = new BApropertyIdentificationStructure()
    propertyIdentity.getContent.addAll(List(uprn, Option(jaxbTextAddress), Option(baReference)).flatten.asJava)
    propertyIdentity
  }

  def typeOfTax = {
    val reasonForReportCode                                = new CtaxReasonForReportCodeStructure()
    val (reasonForReportValue, reasonForReportDescription) = submission match {
      case submission: Cr01Cr03Submission =>
        submission.reasonReport.fold(
          (ebars.xml.CtaxReasonForReportCodeContentType.CR_03, AddProperty.reasonForCodeDescription)
        )(rr => (rr.xmlValue, rr.reasonForCodeDescription))
      case _: Cr05Submission              =>
        (ebars.xml.CtaxReasonForReportCodeContentType.CR_05, "Split properties")
    }
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

  def generateReportTrailer(): ReportTrailerStructure = {
    val trailer = new ReportTrailerStructure()
    trailer.setRecordCount(BigInteger.ONE)
    trailer.setTotalCtaxReportCount(BigInteger.ONE)
    trailer.setTotalNNDRreportCount(BigInteger.ZERO)
    trailer.setEntryDateTime(Instant.now().toXml)

    trailer
  }

}
