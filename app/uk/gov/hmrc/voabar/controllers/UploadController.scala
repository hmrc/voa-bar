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

package uk.gov.hmrc.voabar.controllers

import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.mvc.{ControllerComponents, Result}
import uk.gov.hmrc.crypto.{ApplicationCrypto, Crypted}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.voabar.models.{LoginDetails, UploadDetails}
import uk.gov.hmrc.voabar.services.ReportUploadService

import scala.util.{Failure, Success, Try}

@Singleton
class UploadController @Inject()(reportUploadService: ReportUploadService, configuration: Configuration, controllerComponents: ControllerComponents)
  extends BackendController(controllerComponents) {

  lazy val crypto = new ApplicationCrypto(configuration.underlying).JsonCrypto

  def upload() = Action(parse.json[UploadDetails]) { implicit request =>
    val headers = request.headers
    val uploadDetails = request.body

    val response = for {
      baCode <- headers.get("BA-Code").toRight(Unauthorized("BA-Code missing"))
      encryptedPassword <- headers.get("password").toRight(Unauthorized("password missing"))
      password <- decryptPassword(encryptedPassword)
    } yield {
      reportUploadService.upload(LoginDetails(baCode, password), uploadDetails.xmlUrl, uploadDetails.reference)
      Ok("")
    }
    response.fold(identity, identity)
  }


  private def decryptPassword(encryptedPassword: String): Either[Result, String] = {
    Try {
      crypto.decrypt(Crypted(encryptedPassword))
    } match {
      case Success(password) => Right(password.value)
      case Failure(exception) => Left(Unauthorized("Unable to decrypt password"))
    }
  }

}
