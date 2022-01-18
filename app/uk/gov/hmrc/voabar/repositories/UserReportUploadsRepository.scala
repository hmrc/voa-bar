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
import com.typesafe.config.ConfigException
import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.voabar.models.{BarError, BarMongoError}
import play.api.libs.json.{Format, Json}
import play.api.Configuration
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.ReadPreference
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONDocument
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.voabar.models

import scala.concurrent.{ExecutionContext, Future}

final case class UserReportUpload(_id: String, userId: String, userPassword: String, lastUpdated: ZonedDateTime = ZonedDateTime.now)

object UserReportUpload {
  implicit val format = Json.format[UserReportUpload]
  final val name = classOf[UserReportUpload].getSimpleName.toLowerCase
}

@Singleton
class DefaultUserReportUploadsRepository @Inject() (
                                                   mongo: ReactiveMongoComponent,
                                                   config: Configuration
                                 )(implicit ec: ExecutionContext)
  extends ReactiveRepository[UserReportUpload, String](
    collectionName = UserReportUpload.name,
    mongo = mongo.mongoConnector.db,
    domainFormat = UserReportUpload.format,
    idFormat = implicitly[Format[String]]
    )
  with UserReportUploadsRepository
{
  private val indexName = UserReportUpload.name
  private val expireAfterSeconds = "expireAfterSeconds"
  private val ttlPath = s"${UserReportUpload.name}.timeToLiveInSeconds"
  private val ttl = config.getOptional[Int](ttlPath)
    .getOrElse(throw new ConfigException.Missing(ttlPath))
  createIndex()
  private def createIndex(): Unit = {
    collection.indexesManager.ensure(Index(Seq(("lastUpdated", IndexType.Descending)), Some(indexName),
      options = BSONDocument(expireAfterSeconds -> ttl))) map {
      result => {
        logger.debug(s"set [$indexName] with value $ttl -> result : $result")
        result
      }
    } recover {
      case e => logger.error("Failed to set TTL index", e)
        false
    }
  }
  override def save(userReportUpload: UserReportUpload): Future[Either[BarError, Unit.type]] = {
    insert(userReportUpload)
      .map(_ => Right(Unit))
      .recover {
        case e: Throwable => {
          val errorMsg = s"Error saving user report upload entry"
          logger.error(errorMsg)
          Left(models.BarMongoError(errorMsg))
        }
      }
  }

  override def getById(id: String): Future[Either[BarError, Option[UserReportUpload]]] = {
    findById(id, ReadPreference.primary)
      .map(Right(_))
      .recover {
        case e: Throwable => {
          val errorMsg = s"Error getting user report upload entry for $id"
          logger.error(errorMsg)
          Left(BarMongoError(errorMsg))
        }
      }
  }
}

@ImplementedBy(classOf[DefaultUserReportUploadsRepository])
trait UserReportUploadsRepository {
  def getById(id: String): Future[Either[BarError, Option[UserReportUpload]]]
  def save(userReportUpload: UserReportUpload): Future[Either[BarError, Unit.type]]
}