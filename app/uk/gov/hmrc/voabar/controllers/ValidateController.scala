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

package uk.gov.hmrc.voabar.controllers

import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.mvc.ControllerComponents
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.voabar.services.V1ValidationService

import java.nio.file.Files
import scala.concurrent.{ExecutionContext, Future, blocking}

@Singleton
class ValidateController @Inject() (controllerComponents: ControllerComponents,
                                    v1ValidationService: V1ValidationService
                                   )(implicit ec: ExecutionContext)
  extends BackendController(controllerComponents) {

  val logger = Logger("v2-validation")

  def validate(baLogin: String) = Action.async(parse.temporaryFile) { implicit request =>

    Future {

      val headerCarrier = HeaderCarrierConverter.fromRequest(request)

      val requestId = headerCarrier.requestId.map(_.value).getOrElse("None")

      val v1ProcessingStatus = request.headers.get("X-autobars-processing-status").getOrElse("None")

      val rawXmlData = blocking {
        Files.readAllBytes(request.body.path)
      }

      v1ValidationService.fixAndValidateAsV2(rawXmlData, baLogin, requestId, v1ProcessingStatus)

      Ok("")
    }

  }

}
