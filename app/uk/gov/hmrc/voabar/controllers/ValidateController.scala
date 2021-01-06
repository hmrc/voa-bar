/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.voabar.controllers

import org.apache.commons.io.{FileUtils, IOUtils}

import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.mvc.ControllerComponents
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.bootstrap.controller.BackendController
import uk.gov.hmrc.voabar.services.{V1ValidationService, ValidationService}

import scala.concurrent.{ExecutionContext, Future, blocking}

@Singleton
class ValidateController @Inject() (controllerComponents: ControllerComponents,
                                    v1ValidationService: V1ValidationService
                                   )(implicit ec: ExecutionContext)
  extends BackendController(controllerComponents) {

  val logger = Logger("v2-validation")

  def validate(baLogin: String) = Action.async(parse.temporaryFile) { implicit request =>

    Future {

      val headerCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers, None)

      val requestId = headerCarrier.requestId.map(_.value).getOrElse("None")

      val v1ProcessingStatus = request.headers.get("X-autobars-processing-status").getOrElse("None")

      val rawXmlData = blocking {
        FileUtils.readFileToByteArray(request.body.path.toFile)
      }

      v1ValidationService.fixAndValidateAsV2(rawXmlData, baLogin, requestId, v1ProcessingStatus)

      Ok("")
    }

  }

}
