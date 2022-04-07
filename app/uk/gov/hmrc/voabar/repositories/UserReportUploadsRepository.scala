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

package uk.gov.hmrc.voabar.repositories

import java.time.ZonedDateTime
import com.google.inject.ImplementedBy

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.voabar.models.BarError
import play.api.libs.json.{Json, OFormat}
import play.api.{Configuration, Logging}

import scala.concurrent.{ExecutionContext, Future}
import org.mongodb.scala.ReadPreference
import org.mongodb.scala.model._
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.voabar.util.PlayMongoUtil.{byId, handleMongoError, indexOptionsWithTTL}

final case class UserReportUpload(_id: String, userId: String, userPassword: String, lastUpdated: ZonedDateTime = ZonedDateTime.now)

object UserReportUpload {
  implicit val format: OFormat[UserReportUpload] = Json.format[UserReportUpload]
  final val name = classOf[UserReportUpload].getSimpleName.toLowerCase
}

@Singleton
class DefaultUserReportUploadsRepository @Inject() (
                                                   mongo: MongoComponent,
                                                   config: Configuration
                                 )(implicit ec: ExecutionContext)
  extends PlayMongoRepository[UserReportUpload](
    collectionName = UserReportUpload.name,
    mongoComponent = mongo,
    domainFormat = UserReportUpload.format,
    indexes = Seq(
      IndexModel(Indexes.descending("lastUpdated"), indexOptionsWithTTL(UserReportUpload.name, UserReportUpload.name, config))
    )
  ) with UserReportUploadsRepository with Logging {

  override def save(userReportUpload: UserReportUpload): Future[Either[BarError, Unit]] =
    collection.insertOne(userReportUpload).toFuture()
      .map(_ => Right(()))
      .recover {
        case ex: Throwable => handleMongoError("Error saving user report upload entry", ex, logger)
      }

  override def getById(id: String): Future[Either[BarError, Option[UserReportUpload]]] =
    collection.withReadPreference(ReadPreference.primary())
      .find(byId(id)).headOption()
      .map(Right(_))
      .recover {
        case ex: Throwable => handleMongoError(s"Error getting user report upload entry for $id", ex, logger)
      }

}

@ImplementedBy(classOf[DefaultUserReportUploadsRepository])
trait UserReportUploadsRepository {
  def getById(id: String): Future[Either[BarError, Option[UserReportUpload]]]
  def save(userReportUpload: UserReportUpload): Future[Either[BarError, Unit]]
}
