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

package uk.gov.hmrc.voabar.services

import ebars.xml.{BAreportBodyStructure, BAreports, CtaxReasonForReportCodeContentType}
import ebars.xml.CtaxReasonForReportCodeContentType._
import jakarta.xml.bind.JAXBElement
import models.Purpose
import uk.gov.hmrc.voabar.models.{EmptyReportValidation, ReportErrorDetail, ReportValidation}
import uk.gov.hmrc.voabar.models.{ReportErrorDetailCode => ErrorCode}

import scala.jdk.CollectionConverters._
import scala.language.postfixOps

/**
 * Created by rgallet on 16/02/16.
 */
class RulesValidationEngine {

  def applyRules(baReports: BAreports) = {
    val v = purpose(baReports) match {
      case Purpose.CT  => ReportValidation(Seq.empty[ReportErrorDetail], baReports) map
          CtValidationRules.Cr01AndCr02MissingExistingEntryValidation.apply map
          CtValidationRules.Cr03AndCr04MissingProposedEntryValidation.apply map
          CtValidationRules.Cr05AndCr12MissingProposedEntryValidation.apply map
          CtValidationRules.Cr06AndCr07AndCr09AndCr10AndCr14MissingProposedEntryValidation.apply map
          CtValidationRules.Cr08InvalidCodeValidation.apply map
          TextAddressPostcodeValidation.apply map
          OccupierContactAddressesPostcodeValidation.apply map
          RemarksValidation.apply map
          PropertyPlanReferenceNumberValidation.apply
      case Purpose.NDR => ReportValidation(Seq.empty[ReportErrorDetail], baReports) map
          NdrValidationRules.NdrCodeValidation.apply map
          NdrValidationRules.Rt01AndRt04AndRt03AndRt04MissingProposedEntryValidation.apply map
          NdrValidationRules.Rt05AndRt06AndRt07AndRt08AndRt9AndRt11MissingExistingEntryValidation.apply map
          TextAddressPostcodeValidation.apply map
          OccupierContactAddressesPostcodeValidation.apply map
          RemarksValidation.apply map
          PropertyPlanReferenceNumberValidation.apply
      case _           => EmptyReportValidation()
    }

    v.get
  }

  /**
   * Not final version, what if tax code is not there ? TODO - improve validation on report.
   * @param baReports
   * @return
   */
  def purpose(baReports: BAreports) =
    baReports.getBApropertyReport.asScala.headOption
      .flatMap { report =>
        report.getContent.asScala
          .find(x => !x.isNil && x.getName.getLocalPart == "TypeOfTax")
      }.map(x => x.asInstanceOf[JAXBElement[BAreportBodyStructure.TypeOfTax]].getValue)
      .map { typeOfTaxElement =>
        val cTax   = Option(typeOfTaxElement.getCtaxReasonForReport).flatMap(x => Option(x.getReasonForReportCode))
        val ndrTax = Option(typeOfTaxElement.getNNDRreasonForReport).flatMap(x => Option(x.getReasonForReportCode))
        (cTax, ndrTax) match {
          case (Some(ct), Some(ndr)) => throw new RuntimeException(s"Invalid tax, two codes in XML, ct: $ct ndr: $ndr")
          case (Some(_), None)       => Purpose.CT
          case (None, Some(_))       => Purpose.NDR
          case (None, None)          => throw new RuntimeException("No tax code specified")
        }
      }.getOrElse(throw new RuntimeException("Unable to find type of tax"))

}

sealed trait ValidationRule {

  def apply: (BAreports) => Option[ReportErrorDetail]
}

case object NdrValidationRules {

  case object Rt01AndRt04AndRt03AndRt04MissingProposedEntryValidation extends ValidationRule {

    override def apply: (BAreports) => Option[ReportErrorDetail] = { baReports =>

      lazy val proposed = EbarsXmlCutter.findFirstProposedEntriesIdx(baReports)

      EbarsXmlCutter.extractCR(baReports) match {
        case Some("1") | Some("2") | Some("3") | Some("4") if proposed.isEmpty =>
          Some(ReportErrorDetail(ErrorCode.Rt01AndRt04AndRt03AndRt04MissingProposedEntryValidation))
        case _                                                                 => None
      }
    }
  }

  case object Rt05AndRt06AndRt07AndRt08AndRt9AndRt11MissingExistingEntryValidation extends ValidationRule {

    override def apply: (BAreports) => Option[ReportErrorDetail] = { baReports =>

      lazy val existing = EbarsXmlCutter.findFirstExistingEntriesIdx(baReports)

      EbarsXmlCutter.extractCR(baReports) match {
        case Some(v) if Seq("5", "6", "7", "8", "9", "11").contains(v) && existing.isEmpty =>
          Some(ReportErrorDetail(ErrorCode.Rt05AndRt06AndRt07AndRt08AndRt9AndRt11MissingExistingEntryValidation))
        case _                                                                             => None
      }
    }
  }

  case object NdrCodeValidation extends ValidationRule {
    val validCodes = (1 to 19).map(x => "%02d".format(x)).toSet

    override def apply: BAreports => Option[ReportErrorDetail] = { baReports =>
      EbarsXmlCutter.extractCR(baReports) match {
        case Some(v: String) if validCodes.contains(v)   =>
          None
        case Some(v: String)                             => Some(ReportErrorDetail(ErrorCode.InvalidNdrCode, Seq(v)))
        case Some(v: CtaxReasonForReportCodeContentType) => Some(ReportErrorDetail(ErrorCode.InvalidNdrCode, Seq(v.value())))
        case _                                           => Some(ReportErrorDetail(ErrorCode.NoNDRCode))
      }
    }
  }

}

case object CtValidationRules {

  case object Cr01AndCr02MissingExistingEntryValidation extends ValidationRule {

    override def apply: (BAreports) => Option[ReportErrorDetail] = { baReports =>

      lazy val existing = EbarsXmlCutter.findFirstExistingEntriesIdx(baReports)

      EbarsXmlCutter.extractCR(baReports) match {
        case Some(CR_01) | Some(CR_02) if existing.isEmpty =>
          Some(ReportErrorDetail(ErrorCode.Cr01AndCr02MissingExistingEntryValidation))
        case _                                             => None
      }
    }
  }

  case object Cr03AndCr04MissingProposedEntryValidation extends ValidationRule {

    override def apply: (BAreports) => Option[ReportErrorDetail] = { baReports =>

      lazy val proposed = EbarsXmlCutter.findFirstProposedEntriesIdx(baReports)

      EbarsXmlCutter.extractCR(baReports) match {
        case Some(CR_03) | Some(CR_04) if proposed.isEmpty =>
          Some(ReportErrorDetail(ErrorCode.Cr03AndCr04MissingProposedEntryValidation))
        case _                                             => None
      }
    }
  }

  case object Cr05AndCr12MissingProposedEntryValidation extends ValidationRule {

    override def apply: (BAreports) => Option[ReportErrorDetail] = { baReports =>

      lazy val existing = EbarsXmlCutter.findFirstExistingEntriesIdx(baReports)
      lazy val proposed = EbarsXmlCutter.findFirstProposedEntriesIdx(baReports)

      EbarsXmlCutter.extractCR(baReports) match {
        case Some(CR_05) | Some(CR_12) if proposed.isEmpty || existing.isEmpty =>
          Some(ReportErrorDetail(ErrorCode.Cr05AndCr12MissingProposedEntryValidation))
        case _                                                                 => None
      }
    }
  }

  case object Cr06AndCr07AndCr09AndCr10AndCr14MissingProposedEntryValidation extends ValidationRule {

    override def apply: (BAreports) => Option[ReportErrorDetail] = { baReports =>

      lazy val existing = EbarsXmlCutter.findFirstExistingEntriesIdx(baReports)

      EbarsXmlCutter.extractCR(baReports) match {
        case Some(v) if Seq(CR_06, CR_07, CR_09, CR_10, CR_14).contains(v) && existing.isEmpty =>
          Some(ReportErrorDetail(ErrorCode.Cr06AndCr07AndCr09AndCr10AndCr14MissingProposedEntryValidation))
        case _                                                                                 => None
      }
    }
  }

  case object Cr08InvalidCodeValidation extends ValidationRule {

    override def apply: (BAreports) => Option[ReportErrorDetail] = { baReports =>
      EbarsXmlCutter.extractCR(baReports) match {
        case Some(CR_08) => Some(ReportErrorDetail(ErrorCode.Cr08InvalidCodeValidation))
        case Some(CR_11) => Some(ReportErrorDetail(ErrorCode.Cr11InvalidCodeValidation))
        case Some(CR_13) => Some(ReportErrorDetail(ErrorCode.Cr13InvalidCodeValidation))
        case _           => None
      }
    }
  }

}

case object TextAddressPostcodeValidation extends ValidationRule {
  val postcodePattern = "([A-Z]{1,2}[0-9R][0-9A-Z]? [0-9][A-Z-[CIKMOV]]{2})".r

  override def apply: (BAreports) => Option[ReportErrorDetail] = { baReports =>

    val f = EbarsXmlCutter.getTextAddressStructures(baReports) map (_.getPostcode) flatMap {
      case v if v == null || v.isEmpty => None
      case postcodePattern(zip)        => None
      case v                           => Some(ReportErrorDetail(ErrorCode.TextAddressPostcodeValidation, Seq(v)))
    }

    f headOption
  }
}

case object OccupierContactAddressesPostcodeValidation extends ValidationRule {
  val postcodePattern = "([A-Z]{1,2}[0-9R][0-9A-Z]? [0-9][A-Z-[CIKMOV]]{2})".r

  override def apply: (BAreports) => Option[ReportErrorDetail] = { baReports =>

    val f = EbarsXmlCutter.getOccupierContactAddresses(baReports) map (_.getPostCode) flatMap {
      case v if v == null || v.isEmpty => None
      case postcodePattern(zip)        => None
      case v                           => Some(ReportErrorDetail(ErrorCode.OccupierContactAddressesPostcodeValidation, Seq(v)))
    }

    f headOption
  }
}

case object RemarksValidation extends ValidationRule {

  val maxChars = 240

  override def apply: BAreports => Option[ReportErrorDetail] = { baReports =>
    EbarsXmlCutter.getRemarks(baReports) flatMap {
      case v if v.length <= 1       => Some(ReportErrorDetail(ErrorCode.RemarksValidationNotEmpty))
      case v if v.length > maxChars => Some(ReportErrorDetail(ErrorCode.RemarksValidationTooLong, Seq(s"The remarks cannot exceed $maxChars characters.")))
      case _                        => None
    }
  }

}

case object PropertyPlanReferenceNumberValidation extends ValidationRule {

  override def apply: (BAreports) => Option[ReportErrorDetail] = { baReports =>
    EbarsXmlCutter.getPropertyPlanReferenceNumber(baReports) flatMap {
      case v if v.length > 25 => Some(ReportErrorDetail(ErrorCode.PropertyPlanReferenceNumberValidation, Seq(v)))
      case _                  => None
    } headOption
  }
}
