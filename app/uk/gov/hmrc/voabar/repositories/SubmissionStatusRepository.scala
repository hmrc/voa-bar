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
import java.time.format.DateTimeFormatter
import com.google.inject.ImplementedBy

import javax.inject.{Inject, Singleton}
import play.api.libs.json.{JsObject, JsString, JsValue, Json}
import play.api.{Configuration, Logging}
import uk.gov.hmrc.voabar.models.{BarError, BarMongoError, Done, Error, Failed, ReportStatus, ReportStatusType, Submitted}
import uk.gov.hmrc.voabar.util.TIMEOUT_ERROR

import scala.concurrent.{ExecutionContext, Future}
import org.bson.types._
import org.mongodb.scala.ReadPreference
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.{and, equal}
import org.mongodb.scala.model.Projections._
import org.mongodb.scala.model.Sorts._
import org.mongodb.scala.model.Updates.{push, pushEach, set, setOnInsert}
import org.mongodb.scala.model._
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.voabar.repositories.SubmissionStatusRepository.submissionsCollectionName
import uk.gov.hmrc.voabar.util.PlayMongoUtil.{_id, byId, handleMongoError, handleMongoWarn, indexOptionsWithTTL}


object SubmissionStatusRepository {
  val submissionsCollectionName = "submissions"
}

@Singleton
class SubmissionStatusRepositoryImpl @Inject()(
                                                mongo: MongoComponent,
                                                config: Configuration
                                              )
                                              (implicit executionContext: ExecutionContext)
  extends PlayMongoRepository[ReportStatus](
    collectionName = submissionsCollectionName,
    mongoComponent = mongo,
    domainFormat = ReportStatus.format,
    indexes = Seq(
      // VOA-3276 For Mongo 4.2 index name must be the original index name used on creating index
      IndexModel(Indexes.hashed("baCode"), IndexOptions().name("null_baCodeIdx")),
      IndexModel(Indexes.descending("created"), indexOptionsWithTTL("null_createdIdx", submissionsCollectionName, config))
    )
  ) with SubmissionStatusRepository with Logging {

  val timeoutMinutes = 120

  def saveOrUpdate(reportStatus: ReportStatus, upsert: Boolean): Future[Either[BarError, Unit.type]] = {
    val reportDataJson = Json.toJson(reportStatus).as[JsObject] - _id
    val modifier = Seq(
      set(reportDataJson.as[BsonDocument])
    )

    atomicSaveOrUpdate(reportStatus.id, modifier, upsert)
  }

  def saveOrUpdate(userId: String, reference: String, upsert: Boolean): Future[Either[BarError, Unit.type]] = {
    val modifier = Seq(
      set("baCode", userId),
      set("created", ZonedDateTime.now.toString)
    )

    atomicSaveOrUpdate(reference, modifier, upsert)
  }

  override def getByUser(baCode: String, filterStatus: Option[String] = None): Future[Either[BarError, Seq[ReportStatus]]] = {
    val isoDate = ZonedDateTime.now().minusDays(90)
      .withHour(3) //Set 3AM to prevent submissions disapper during day.
      .withMinute(0)
      .format(DateTimeFormatter.ISO_DATE_TIME)

    val q = Json.obj(
      "baCode" -> baCode,
      "created" -> Json.obj(
        "$gt" -> isoDate
      )
    )

    val finder = filterStatus.fold(q)(status => q.+("status" -> JsString(status)))

    collection.find(finder).sort(Json.obj("created" -> -1)).cursor[ReportStatus]()
      .collect[Seq](-1, Cursor.FailOnError[Seq[ReportStatus]]())
      .flatMap { res =>
        Future.sequence(res.map(checkAndUpdateSubmissionStatus)).map(Right(_))
      }
      .recover {
        case ex: Throwable => handleMongoWarn(s"Couldn't retrieve BA reports with '$baCode'", ex, logger)
      }
  }

  override def getByReference(reference: String): Future[Either[BarError, ReportStatus]] = {
    collection.find(byId(reference)).sort(Json.obj("created" -> -1)).cursor[ReportStatus](ReadPreference.primary)
      .collect[Seq](1, Cursor.FailOnError[Seq[ReportStatus]]())
      .flatMap { res =>
        checkAndUpdateSubmissionStatus(res.head).map(Right(_))
      }
      .recover {
        case ex: Throwable => handleMongoWarn(s"Couldn't retrieve BA reports for reference $reference", ex, logger)
      }
  }

  override def getAll(): Future[Either[BarError, Seq[ReportStatus]]] = {
    collection.find().sort(Json.obj("created" -> -1)).cursor[ReportStatus](ReadPreference.primary)
      .collect[Seq](-1, Cursor.FailOnError[Seq[ReportStatus]]())
      .flatMap { res =>
        Future.sequence(res.map(checkAndUpdateSubmissionStatus)).map(Right(_))
      }
      .recover {
        case ex: Throwable => handleMongoWarn("Couldn't retrieve all BA reports", ex, logger)
      }
  }

  def addErrors(submissionId: String, errors: List[Error]): Future[Either[BarError, Boolean]] = {
    val modifier = Seq(
      pushEach("errors", errors: _*)
    )

    addErrorsByModifier(submissionId, modifier)
  }

  override def addError(submissionId: String, error: Error): Future[Either[BarError, Boolean]] = {
    val modifier = Seq(
      push("errors", error)
    )

    addErrorsByModifier(submissionId, modifier)
  }

  override def updateStatus(submissionId: String, status: ReportStatusType): Future[Either[BarError, Boolean]] = {
    val modifier = Seq(
      set("status", status.value)
    )

    updateStatusByModifier(submissionId, modifier)
  }

  override def update(submissionId: String, status: ReportStatusType, totalReports: Int): Future[Either[BarError, Boolean]] = {
    val modifier = Seq(
      set("status", status.value),
      set("totalReports", totalReports)
    )

    updateStatusByModifier(submissionId, modifier)
  }

  override def deleteByReference(reference: String, user: String): Future[Either[BarError, JsValue]] = {
    val deleteSelector = and(byId(reference), equal("baCode", user))
    logger.warn(s"Performing deletion on $collectionName with id = $reference, baCode = $user")

    collection.deleteOne(deleteSelector).toFutureOption()
      .map { deleteResult =>
        val response = Json.obj("n" -> deleteResult.map(_.getDeletedCount).getOrElse(0L))
        logger.warn(s"Deletion on $collectionName done, returning response : $response")
        Right(response)
      }
      .recover {
        case ex: Throwable => handleMongoError(s"Deletion failed for $reference, BA: $user", ex, logger)
      }
  }

  private def checkAndUpdateSubmissionStatus(report: ReportStatus): Future[ReportStatus] = {
    if(report.status.exists(x => x == Failed.value || x == Submitted.value || x == Done.value)) {
      Future.successful(report)
    }else {
      if(report.created.compareTo(ZonedDateTime.now().minusMinutes(timeoutMinutes)) < 0) {
        markSubmissionFailed(report)
      }else {
        Future.successful(report)
      }
    }
  }

  private def markSubmissionFailed(report: ReportStatus): Future[ReportStatus] = {
    val update = Seq(
      set("status", Failed.value),
      set("errors", Json.arr(Error(TIMEOUT_ERROR))) // TODO: BSON array !!!
    )

    collection
      .findOneAndUpdate(byId(report.id), update,
        FindOneAndUpdateOptions().upsert(false).returnDocument(ReturnDocument.AFTER))
      .toFutureOption()
      .flatMap {
        case Some(reportStatus) => Future.successful(reportStatus)
        case _ => Future.failed(new IllegalStateException("reportStatus not found for markSubmissionFailed"))
      }
  }

  private def updateStatusByModifier(submissionId: String, modifier: Seq[Bson]): Future[Either[BarMongoError, Boolean]] =
    collection.updateOne(byId(submissionId), modifier).toFutureOption()
      .map {
        case Some(updateResult) if updateResult.getModifiedCount == 1 => Right(true)
        case _ =>
          val errorMsg = s"Report status wasn't updated for $submissionId"
          logger.error(errorMsg)
          Left(BarMongoError(errorMsg))
      }
      .recover {
        case ex: Throwable => handleMongoError(s"Unable to update report status for $submissionId", ex, logger)
      }

  private def addErrorsByModifier(submissionId: String, modifier: Seq[Bson]): Future[Either[BarMongoError, Boolean]] =
    collection.updateOne(byId(submissionId), modifier).toFutureOption()
      .map {
        case Some(updateResult) if updateResult.getModifiedCount == 1 => Right(true)
        case _ =>
          val errorMsg = s"Error message wasn't recorded for $submissionId"
          logger.error(errorMsg)
          Left(BarMongoError(errorMsg))
      }
      .recover {
        case ex: Throwable => handleMongoError(s"Unable to record error message for $submissionId", ex, logger)
      }

  private def atomicSaveOrUpdate(id: String, update: Seq[Bson], upsert: Boolean): Future[Either[BarMongoError, Unit.type]] = {
    val updateDocument = if (upsert) {
      update :+ setOnInsert(_id, id)
    } else {
      update
    }

    collection.findOneAndUpdate(byId(id), updateDocument, FindOneAndUpdateOptions().upsert(upsert)).toFutureOption()
      .map(_ => Right(Unit))
      .recover {
        case ex: Throwable => handleMongoError("Error while saving submission", ex, logger)
      }
  }

}

@ImplementedBy(classOf[SubmissionStatusRepositoryImpl])
trait SubmissionStatusRepository {

  def addError(submissionId: String, error: Error): Future[Either[BarError, Boolean]]

  def updateStatus(submissionId: String, status: ReportStatusType): Future[Either[BarError, Boolean]]

  def update(submissionId: String, status: ReportStatusType, totalReports: Int): Future[Either[BarError, Boolean]]

  def getByUser(userId: String, filter: Option[String] = None) : Future[Either[BarError, Seq[ReportStatus]]]

  def getByReference(reference: String) : Future[Either[BarError, ReportStatus]]

  def deleteByReference(reference: String, user: String) : Future[Either[BarError, JsValue]]

  def getAll(): Future[Either[BarError, Seq[ReportStatus]]]

  def saveOrUpdate(reportStatus: ReportStatus, upsert: Boolean): Future[Either[BarError, Unit.type]]

  def saveOrUpdate(userId: String, reference: String, upsert: Boolean): Future[Either[BarError, Unit.type]]
}
