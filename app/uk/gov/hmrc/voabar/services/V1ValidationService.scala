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

import java.io.ByteArrayInputStream

import ebars.xml.BAreports
import javax.inject.{Inject, Singleton}
import javax.xml.transform.stream.StreamSource
import play.api.Logger
import services.EbarsValidator
import uk.gov.hmrc.voabar.models.LoginDetails

import scala.jdk.CollectionConverters._
import scala.util.Try

@Singleton
class V1ValidationService @Inject()(validationService: ValidationService) {

  val log = Logger(getClass)

  val correctionEngine = new RulesCorrectionEngine

  val rulesValidationEngine = new RulesValidationEngine

  val xmlValidator = new EbarsValidator

  def fixAndValidateAsV2(xml: Array[Byte], baLogin: String, requestId: String, v1Status: String): Boolean = {

    Try {
      val source = new StreamSource(CorrectionInputStream(new ByteArrayInputStream(xml)))

      val submission = xmlValidator.fromXml(source)

      val allReports = xmlValidator.split(submission)

      allReports.foreach { report =>
        correctionEngine.applyRules(report)
      }

      submission.getBApropertyReport.clear()
      submission.getBApropertyReport.addAll(allReports.map(_.getBApropertyReport.get(0)).asJava)

      FixHeader(submission)
      FixCTaxTrailer(submission)

      validateAsV2(submission, baLogin, requestId, v1Status)

    }.recover {
      case e: Exception => {
        log.warn("Unable to validate XML from V1", e)
        false
      }
    }.getOrElse(false)

  }

  def validateAsV2(correctedXml: BAreports, baLogin: String, requestId: String, v1Status: String): Boolean = {
    validationService.validate(correctedXml, LoginDetails(baLogin, "")) match {
      case Left(errors) => {
        log.info(s"Validation of fixed XML, baLogin: ${baLogin}, requestId: ${requestId}, v1Status: ${v1Status}, errors: ${errors}")
        false
      }
      case Right(_) => {
        log.info(s"Validation of fixed XML successful, baLogin: ${baLogin} requestId: ${requestId}, v1Status: ${v1Status}")
        true
      }
    }
  }

}
