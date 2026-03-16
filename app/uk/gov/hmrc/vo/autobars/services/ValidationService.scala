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

package uk.gov.hmrc.vo.autobars.services

import ebars.xml.{BApropertySplitMergeStructure, BAreportBodyStructure, BAreports}
import jakarta.xml.bind.JAXBElement
import play.api.Logging
import services.EbarsValidator
import uk.gov.hmrc.vo.autobars.models.{BarError, BarSubmissionValidationError, BarValidationError, BarXmlError, Error, LoginDetails, ReportError}
import uk.gov.hmrc.vo.autobars.util.ErrorCode.*

import javax.inject.Singleton
import scala.jdk.CollectionConverters.*
import scala.util.Try

@Singleton
class ValidationService extends Logging:

  val eBarsValidator = EbarsValidator()

  def validate(submissions: BAreports, baLogin: LoginDetails): Either[BarError, Unit] =
    logger.warn(s"submissions in XML : ${submissions.getBApropertyReport.size()}, isEmpty ${submissions.getBApropertyReport.isEmpty}")

    if submissions.getBApropertyReport.isEmpty then
      Left(BarXmlError("No submission found."))
    else
      val headerErrors = validateHeaderTrailer(submissions, baLogin)
      if headerErrors.isEmpty then
        val bodyErrors = validateBody(submissions)
        if bodyErrors.isEmpty then Right(()) else Left(BarSubmissionValidationError(bodyErrors))
      else
        Left(BarValidationError(headerErrors))

  private def validateBody(submissions: BAreports): List[ReportError] =
    eBarsValidator.split(submissions).flatMap { submission =>
      validateSubmission(submission)
    }.toList

  /**
    * @param submission only one submission!!!!.
    * @return
    */
  private def validateSubmission(submission: BAreports): Option[ReportError] =
    assert(submission.getBApropertyReport.size() == 1, "Single submission validation can contain only one submission")

    val validation = RulesValidationEngine()
    val errors     = validation.applyRules(submission)

    Option(errors)
      .filter(_.nonEmpty)
      .map(x => createSubmissionDetailDescription(submission).copy(errors = x))

  private def createSubmissionDetailDescription(submission: BAreports): ReportError =
    // TODO should we have assert or just return None, or take head ???
    // TODO maybe delete after full development.
    assert(submission.getBApropertyReport.size() == 1, "createPropertyDescription can create description for only one submission")

    submission.getBApropertyReport.asScala.headOption.map { submission =>
      val reportNumber = submission.getContent.asScala.find(x => x.getName.getLocalPart == "BAreportNumber" && !x.isNil)
        .map(x => x.asInstanceOf[JAXBElement[String]].getValue.trim)
        .filter(_ != "")

      val baTransaction = submission.getContent.asScala.find(x => x.getName.getLocalPart == "TransactionIdentityBA" & !x.isNil)
        .map(x => x.asInstanceOf[JAXBElement[String]].getValue.trim)
        .filter(_ != "")

      val uprn = (extractUPRN(submission, "ProposedEntries") ++ extractUPRN(submission, "ExistingEntries"))
        .distinct
        .sorted

      ReportError(reportNumber, baTransaction, uprn, Seq.empty)
    }.getOrElse(ReportError(None, None, Seq.empty, Seq.empty))

  private def extractUPRN(submission: BAreportBodyStructure, entries: String): Seq[Long] =
    Try {
      submission.getContent.asScala.find(x => x.getName.getLocalPart == entries && !x.isNil)
        .map(x => x.asInstanceOf[JAXBElement[BApropertySplitMergeStructure]].getValue)
        .toList.flatMap(x => x.getAssessmentProperties.asScala.toList)
        .flatMap { x =>
          x.getPropertyIdentity.getContent.asScala
            .find(z => z.getName.getLocalPart == "UniquePropertyReferenceNumber" && !z.isNil)
            .map(z => z.asInstanceOf[JAXBElement[Long]].getValue)
        }
    }.fold(
      e =>
        logger.warn("Unable to extract UPRN: ", e)
        List.empty[Long]
      ,
      identity
    )

  private def validateHeaderTrailer(submission: BAreports, baLogin: LoginDetails): List[Error] =
    validationBACode(submission, baLogin)

  private def validationBACode(submission: BAreports, baLogin: LoginDetails): List[Error] =
    Option(submission.getBAreportHeader.getBillingAuthorityIdentityCode) match
      case None                                     => List(Error(BA_CODE_REPORT, Seq("'BAidentityNumber' missing.")))
      case Some(baCode) if baCode == 0              => List(Error(BA_CODE_REPORT, Seq("'BAidentityNumber' missing.")))
      case Some(baCode) if baCode == baLogin.baCode => List.empty
      case Some(wrongBaNumber)                      => List(Error(BA_CODE_MATCH, Seq(wrongBaNumber.toString)))
