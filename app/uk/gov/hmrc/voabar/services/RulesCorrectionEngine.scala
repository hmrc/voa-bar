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

import java.io.{FilterInputStream, InputStream}
import java.math.BigInteger
import java.time.{ZoneId, ZonedDateTime}
import java.util.GregorianCalendar

import ebars.xml.CtaxReasonForReportCodeContentType._
import ebars.xml.{BAreportBodyStructure, BAreports}
import io.inbot.utils.ReplacingInputStream
import jakarta.xml.bind.JAXBElement
import javax.xml.datatype.{DatatypeConstants, DatatypeFactory}
import javax.xml.namespace.QName
import models.EbarsBAreports._
import models.Purpose
import org.apache.commons.lang3.StringUtils

import scala.util.{Success, Try}

/**
  * Created by rgallet on 12/02/16.
  */
class RulesCorrectionEngine {

  val ctRules = Seq(
    RemoveBS7666Addresses, RemovePropertyGridCoords,
    CtRules.Cr01AndCr02AndCr06AndCr07AndCr09AndCr10AndCr14MissingExistingEntry,
    CtRules.Cr01AndCr02AndCr06AndCr07AndCr09AndCr10AndCr14RemoveProposedEntries,
    CtRules.Cr03AndCr04BothProposedAndExistingEntries,
    CtRules.Cr03AndCr04MissingProposedEntry,
    CtRules.Cr05AndCr12MissingAnyEntry,
    CtRules.Cr05CopyProposedEntriesToExistingEntries,
    CtRules.Cr12CopyProposedEntriesToRemarks,
    PostcodesToUppercase,
    RemarksTrimmer,
    RemarksFillDefault,
    RemovingInvalidTaxBand,
    PropertyDescriptionTextRemoval
  )

  val ndrRules = Seq(
    RemoveBS7666Addresses, RemovePropertyGridCoords,
    NdrRules.Rt01AndRt02AndRt03AndRt04MissingProposedEntry,
    NdrRules.Rt05AndRt06AndRt07AndRt08AndRt09AndRt11MissingExistingEntry,
    NdrRules.Rt01AndRt02AndRt03AndRt04RemoveExistingEntries,
    NdrRules.Rt05AndRt06AndRt07AndRt08AndRt09AndRt11RemoveProposedEntries,
    PostcodesToUppercase,
    RemarksTrimmer,
    RemarksFillDefault
  )

  def applyRules(baReports: BAreports): Unit =
    baReports.purpose match {
      case Purpose.CT => ctRules foreach (_.apply(baReports))
      case Purpose.NDR => ndrRules foreach (_.apply(baReports))
      case _ => ()
    }
}

sealed trait Rule {
  def apply(baReports: BAreports): Unit
}

object CorrectionInputStream {

  def apply(input: InputStream): FilterInputStream = {
    //In future we can add mode replacement. We want to replace on byte level to prevent problem with different XML charset.
    new ReplacingInputStream(input, "&nbsp;", " ")
  }

}

case object FixHeader extends Rule {
  val zoneId = ZoneId.of("Europe/London")

  override def apply(baReports: BAreports): Unit = {
    val header = baReports.getBAreportHeader
    if (header.getEntryDateTime == null) {
      val now = ZonedDateTime.now(zoneId)
      val xmlNow = DatatypeFactory.newInstance().newXMLGregorianCalendar(GregorianCalendar.from(now))
      header.setEntryDateTime(xmlNow)
    }
    if (header.getProcessDate == null) {
      val now = ZonedDateTime.now(zoneId)
      val xmlNow = DatatypeFactory.newInstance()
        .newXMLGregorianCalendarDate(now.getYear, now.getMonthValue, now.getDayOfMonth, DatatypeConstants.FIELD_UNDEFINED)
      header.setProcessDate(xmlNow)
    }
  }
}

case object FixCTaxTrailer extends Rule {

  val zoneId = ZoneId.of("Europe/London")

  override def apply(baReports: BAreports): Unit = {
    val trailer = baReports.getBAreportTrailer
    //Always set properly number of records
    trailer.setRecordCount(BigInteger.valueOf(baReports.getBApropertyReport.size()))
    //Always set properly number of CT reports, we support only CT at the moment
    trailer.setTotalCtaxReportCount(BigInteger.valueOf(baReports.getBApropertyReport.size()))
    //NDR REPORTS are not supported
    trailer.setTotalNNDRreportCount(BigInteger.ZERO)
    if (trailer.getEntryDateTime == null) {
      val now = ZonedDateTime.now(zoneId)
      val xmlNow = DatatypeFactory.newInstance().newXMLGregorianCalendar(GregorianCalendar.from(now))
      trailer.setEntryDateTime(xmlNow)
    }
  }
}


/**
 * Strip whitespace characters in remarks element.
 */
case object RemarksTrimmer extends Rule {

  val firstStageRegex = """(\p{javaSpaceChar}|\p{javaWhitespace}|\s)""".r  //First replace all obscure space and newline with space
  val secondStageRegex = """\s{2,}""".r

  override def apply(baReports: BAreports): Unit = {
    assert(baReports.getBApropertyReport.size() == 1,
      s"Rules correction engine can update only single report, multiple or zero report present: ${baReports.getBApropertyReport.size()} report(s)")

    val content = baReports.getBApropertyReport.get(0).getContent

    EbarsXmlCutter.findRemarksIdx(baReports) foreach { idx =>
      val remarks = content.get(idx).asInstanceOf[JAXBElement[String]]
      remarks.getValue match {
        case null | "" => //nothing
        case value => {
          val firstStageResult = firstStageRegex.replaceAllIn(value, " ")
          val secondStageResult = secondStageRegex.replaceAllIn(firstStageResult, " ")
          val newRemarksValue = StringUtils.strip(secondStageResult)
          remarks.setValue(newRemarksValue)
        }
      }
    }
  }

}

case object RemarksFillDefault extends Rule {
  override def apply(baReports: BAreports): Unit = {
    val qName = new QName("http://www.govtalk.gov.uk/LG/Valuebill", "Remarks")
    val newRemarks = new JAXBElement(qName, classOf[String], classOf[BAreportBodyStructure], "NO REMARKS")
    val content = baReports.getBApropertyReport.get(0).getContent

    EbarsXmlCutter.findRemarksIdx(baReports) foreach { idx =>
      val remarks = content.get(idx)

      remarks.getValue.asInstanceOf[String] match {
        case null | "" =>
          content.remove(idx)
          content.add(idx, newRemarks)
        case _ => //nothing
      }
    }

    EbarsXmlCutter.findRemarksIdx(baReports).isEmpty match {
      case true => content.add(newRemarks)
      case _ => //nothing
    }
  }
}

case object RemoveBS7666Addresses extends Rule {
  override def apply(baReports: BAreports): Unit = {
    EbarsXmlCutter.removeBS7666Address(baReports)
  }
}

case object RemovePropertyGridCoords extends Rule {
  override def apply(baReports: BAreports): Unit = {
    EbarsXmlCutter.removePropertyGridCoords(baReports)
  }
}

case object RemovingInvalidTaxBand extends Rule {
  override def apply(baReports: BAreports): Unit = {
    EbarsXmlCutter.removeNullCurrentTax(baReports)
  }
}

case object PropertyDescriptionTextRemoval extends Rule {
  override def apply(baReports: BAreports): Unit = {
    EbarsXmlCutter.AssessmentProperties(baReports)
      .filter(_.getPropertyDescription != null)
      .filter(_.getPropertyDescription.getPropertyDescriptionText != null)
      .filter(_.getPropertyDescription.getPropertyDescriptionText.length <= 1) foreach {
      _.setPropertyDescription(null)
    }
  }
}


case object NdrRules {

  case object Rt05AndRt06AndRt07AndRt08AndRt09AndRt11MissingExistingEntry extends Rule {
    val codes = Seq("5", "6", "7", "8", "9", "11")

    override def apply(baReports: BAreports): Unit = {

      lazy val existing = EbarsXmlCutter.findFirstExistingEntriesIdx(baReports)
      lazy val proposed = EbarsXmlCutter.findFirstProposedEntriesIdx(baReports)

      EbarsXmlCutter.CR(baReports) match {
        case Some(v) if codes.contains(v) && existing.isEmpty && proposed.nonEmpty => EbarsXmlCutter.convertProposedEntriesIntoExistingEntries(baReports)
        case _ => //nothing to do
      }
    }
  }

  case object Rt01AndRt02AndRt03AndRt04MissingProposedEntry extends Rule {
    val codes = Seq("1", "2", "3", "4")

    override def apply(baReports: BAreports): Unit = {

      lazy val proposed = EbarsXmlCutter.findFirstProposedEntriesIdx(baReports)
      lazy val existing = EbarsXmlCutter.findFirstExistingEntriesIdx(baReports)

      EbarsXmlCutter.CR(baReports) match {
        case Some(v) if codes.contains(v) && proposed.isEmpty && existing.nonEmpty => EbarsXmlCutter.convertExistingEntriesIntoProposedEntries(baReports)
        case _ => //nothing to do
      }
    }
  }

  case object Rt05AndRt06AndRt07AndRt08AndRt09AndRt11RemoveProposedEntries extends Rule {
    val codes = Seq("5", "6", "7", "8", "9", "11")

    override def apply(baReports: BAreports): Unit = {

      lazy val proposed = EbarsXmlCutter.findFirstProposedEntriesIdx(baReports)
      lazy val existing = EbarsXmlCutter.findFirstExistingEntriesIdx(baReports)

      EbarsXmlCutter.CR(baReports) match {
        case Some(v) if codes.contains(v) && proposed.nonEmpty && existing.nonEmpty => EbarsXmlCutter.removeProposedEntries(baReports)
        case _ => //nothing to do
      }
    }
  }

  case object Rt01AndRt02AndRt03AndRt04RemoveExistingEntries extends Rule {
    val codes = Seq("1", "2", "3", "4")

    override def apply(baReports: BAreports): Unit = {

      lazy val proposed = EbarsXmlCutter.findFirstProposedEntriesIdx(baReports)
      lazy val existing = EbarsXmlCutter.findFirstExistingEntriesIdx(baReports)

      EbarsXmlCutter.CR(baReports) match {
        case Some(v) if codes.contains(v) && proposed.nonEmpty && existing.nonEmpty => EbarsXmlCutter.removeExistingEntries(baReports)
        case _ => //nothing to do
      }
    }
  }
}

case object CtRules {

  case object Cr01AndCr02AndCr06AndCr07AndCr09AndCr10AndCr14MissingExistingEntry extends Rule {
    val codes = Seq(CR_01, CR_02, CR_06, CR_07, CR_09, CR_10, CR_14)

    override def apply(baReports: BAreports): Unit = {

      lazy val existing = EbarsXmlCutter.findFirstExistingEntriesIdx(baReports)
      lazy val proposed = EbarsXmlCutter.findFirstProposedEntriesIdx(baReports)

      EbarsXmlCutter.CR(baReports) match {
        case Some(v) if codes.contains(v) && existing.isEmpty && proposed.nonEmpty => EbarsXmlCutter.convertProposedEntriesIntoExistingEntries(baReports)
        case _ => //nothing to do
      }
    }
  }

  case object Cr01AndCr02AndCr06AndCr07AndCr09AndCr10AndCr14RemoveProposedEntries extends Rule {
    val codes = Seq(CR_01, CR_02, CR_06, CR_07, CR_09, CR_10, CR_14)

    override def apply(baReports: BAreports): Unit = {

      lazy val proposed = EbarsXmlCutter.findFirstProposedEntriesIdx(baReports)
      lazy val existing = EbarsXmlCutter.findFirstExistingEntriesIdx(baReports)

      EbarsXmlCutter.CR(baReports) match {
        case Some(v) if codes.contains(v) && proposed.nonEmpty && existing.nonEmpty => EbarsXmlCutter.removeProposedEntries(baReports)
        case _ => //nothing to do
      }
    }
  }

  case object Cr03AndCr04BothProposedAndExistingEntries extends Rule {
    val codes = Seq(CR_03, CR_04)

    override def apply(baReports: BAreports): Unit = {

      lazy val proposed = EbarsXmlCutter.findFirstProposedEntriesIdx(baReports)
      lazy val existing = EbarsXmlCutter.findFirstExistingEntriesIdx(baReports)

      EbarsXmlCutter.CR(baReports) match {
        case Some(v) if codes.contains(v) && proposed.nonEmpty && existing.nonEmpty => EbarsXmlCutter.removeExistingEntries(baReports)
        case _ => //nothing to do
      }
    }
  }

  case object Cr03AndCr04MissingProposedEntry extends Rule {
    val codes = Seq(CR_03, CR_04)

    override def apply(baReports: BAreports): Unit = {

      lazy val proposed = EbarsXmlCutter.findFirstProposedEntriesIdx(baReports)
      lazy val existing = EbarsXmlCutter.findFirstExistingEntriesIdx(baReports)

      EbarsXmlCutter.CR(baReports) match {
        case Some(v) if codes.contains(v) && proposed.isEmpty && existing.nonEmpty => EbarsXmlCutter.convertExistingEntriesIntoProposedEntries(baReports)
        case _ => //nothing to do
      }
    }
  }

  case object Cr05AndCr12MissingAnyEntry extends Rule {
    val codes = Seq(CR_05, CR_12)

    override def apply(baReports: BAreports): Unit = {

      lazy val existing = EbarsXmlCutter.findFirstExistingEntriesIdx(baReports)
      lazy val proposed = EbarsXmlCutter.findFirstProposedEntriesIdx(baReports)

      EbarsXmlCutter.CR(baReports) match {
        case Some(v) if codes.contains(v) && proposed.isEmpty && existing.nonEmpty => EbarsXmlCutter.copyExistingEntriesToProposed(baReports)
        case Some(v) if codes.contains(v) && existing.isEmpty && proposed.nonEmpty => EbarsXmlCutter.copyProposedEntriesToExisting(baReports)
        case _ => //nothing to do
      }
    }
  }

  /**
    * CR05 reports need to have 1+ ProposedEntries and 1+ ExistingEntries to validate the XSD schema.
    *
    * However, there is a bug with CR05 codes in the legacy ebars which discards the data present in <ProposedEntries>/<AssessmentProperties>/<TextAddress>,
    * hence rendering the submission pointless. However, the data in <ProposedEntries>/<OccupierContact> does come across. ;-)
    *
    * To mitigate the bug above, we append all the <ProposedEntries>/<AssessmentProperties> into <ExistingEntries>. In other words,
    * both <ProposedEntries>/<AssessmentProperties>/<TextAddress> end up in <ExistingEntries>/<AssessmentProperties>+ with a prefix value of [PROPOSED]
    * for <ProposedEntries>/<AssessmentProperties>/<TextAddress>/<AddressLine>
    *
    * Because the data in <ProposedEntries>/<AssessmentProperties>/<OccupierContact> does come across, we also remove the <ProposedEntries> altogether.
    * This avoids duplication of <OccupierContact> elements in CDB.
    *
    */
  case object Cr05CopyProposedEntriesToExistingEntries extends Rule {
    val codes = Seq(CR_05)

    override def apply(baReports: BAreports): Unit = {

      lazy val existing = EbarsXmlCutter.findFirstExistingEntriesIdx(baReports)
      lazy val proposed = EbarsXmlCutter.findFirstProposedEntriesIdx(baReports)

      EbarsXmlCutter.CR(baReports) match {
        case Some(v) if codes.contains(v) && existing.nonEmpty && proposed.nonEmpty => EbarsXmlCutter.appendProposedEntriesToExisting(baReports)
        case _ => //nothing to do
      }
    }
  }

  /**
    * CR12 reports need to have 1 ProposedEntries and 1 ExistingEntries to validate the XSD schema.
    *
    * However, there is a bug with CR12 codes in the legacy ebars which discards the data present in <ProposedEntries>/<AssessmentProperties>/<TextAddress>,
    * hence rendering the submission pointless. However, the data in <ProposedEntries>/<AssessmentProperties>/<OccupierContact> does come across. ;-)
    *
    * To mitigate the bug above, we append all the <ProposedEntries>/<AssessmentProperties> into <BApropertyReport>/<Remarks> with a prefix value of [PROPOSED]
    * for <ProposedEntries>/<TextAddress>/<AddressLine>
    *
    */
  case object Cr12CopyProposedEntriesToRemarks extends Rule {
    val codes = Seq(CR_12)

    override def apply(baReports: BAreports): Unit = {

      lazy val proposed = EbarsXmlCutter.findFirstProposedEntriesIdx(baReports)

      EbarsXmlCutter.CR(baReports) match {
        case Some(v) if codes.contains(v) && proposed.nonEmpty => EbarsXmlCutter.appendProposedEntriesToRemarks(baReports)
        case _ => //nothing to do
      }
    }
  }
}

case object PostcodesToUppercase extends Rule {
  override def apply(baReports: BAreports): Unit = {

    def sanitising(postcode: String) = {
      Try {
        val trimmed = postcode.toUpperCase.trim.replaceAll("\\s", "")       //TODO - implement as webBars
        trimmed.substring(0, trimmed.length - 3) + " " + trimmed.substring(trimmed.length - 3) // and validate, if not valid keep original postcode
      } match {
        case Success(v) => v
        case _ => postcode
      }
    }

    EbarsXmlCutter.TextAddressStructures(baReports) foreach { textAddressStructure =>
      textAddressStructure.getPostcode match {
        case null => //nothing
        case v => textAddressStructure.setPostcode(sanitising(v.toUpperCase))
      }
    }

    EbarsXmlCutter.OccupierContactAddresses(baReports) foreach { occupierContactAddress =>
      occupierContactAddress.getPostCode match {
        case null => //nothing
        case v => occupierContactAddress.setPostCode(sanitising(v.toUpperCase))
      }
    }
  }
}
