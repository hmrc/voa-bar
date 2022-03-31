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

package uk.gov.hmrc.voabar.models

import play.api.libs.json.Json
import uk.gov.hmrc.voabar.util.ErrorCode
import org.bson.types._
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.model._


case class Error(code: ErrorCode, values: Seq[String] = Seq(), submissionDetail: Option[String] = None)

object Error {
  implicit val format = Json.format[Error]
  implicit val errorHandler: BSONHandler[BsonDocument, Error] =  Macros.handler[Error]

}
