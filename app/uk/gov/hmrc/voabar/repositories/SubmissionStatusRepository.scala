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
import play.api.libs.json.{Format, JsObject, JsString, JsValue, Json}
import play.api.{Configuration, Logger}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.{Cursor, ReadPreference}
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONDocument
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.mongo.{BSONBuilderHelpers, ReactiveRepository}
import uk.gov.hmrc.voabar.models.{BarError, BarMongoError, Done, Error, Failed, ReportStatus, ReportStatusType, Submitted}
import uk.gov.hmrc.voabar.util.TIMEOUT_ERROR

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SubmissionStatusRepositoryImpl @Inject()(
                                                mongo: ReactiveMongoComponent,
                                                config: Configuration
                                              )
                                              (implicit executionContext: ExecutionContext)
  extends ReactiveRepository[ReportStatus, String](
    collectionName = "submissions",
    mongo = mongo.mongoConnector.db,
    domainFormat = ReportStatus.format,
    idFormat = implicitly[Format[String]]
  ) with SubmissionStatusRepository with BSONBuilderHelpers {

  val timeoutMinutes = 120

  private val ttlPath = s"$collectionName.timeToLiveInSeconds"
  private val ttl = config.get[Int](ttlPath)

  private val log = Logger(this.getClass)
  override def indexes: Seq[Index] = Seq (
    // VOA-3276 For Mongo 4.2 index name must be the original index name used on creating index
    Index(Seq("baCode" -> IndexType.Hashed), name = Some("null_baCodeIdx")),
    Index(Seq("created" -> IndexType.Descending), name = Some("null_createdIdx")
      ,options = BSONDocument("expireAfterSeconds" -> ttl)) //TODO - This is broken, data are store as String(VOA-2189)
  )

  def saveOrUpdate(reportStatus: ReportStatus, upsert: Boolean): Future[Either[BarError, Unit.type]] = {
    //TODO - Please refactor(probably whole repository) and use as much as possible ReactiveRepository functionality.
    val finder = BSONDocument(_Id -> reportStatus.id)
    val reportData = Json.toJson(reportStatus).as[JsObject].-("_id")
    atomicSaveOrUpdate(reportStatus.id, upsert, finder, set(reportData.as[BSONDocument]))
  }

  def saveOrUpdate(userId: String, reference: String, upsert: Boolean)
  : Future[Either[BarError, Unit.type]] = {
    val finder = BSONDocument(_Id -> reference)
    val modifierBson = set(BSONDocument(
      "created" -> ZonedDateTime.now.toString,
      "baCode" -> userId)
    )

    atomicSaveOrUpdate(reference, upsert, finder, modifierBson)
  }

  override def getByUser(baCode: String, filter: Option[String] = None)
  : Future[Either[BarError, Seq[ReportStatus]]] = {

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

    val finder = filter.fold(q)(f => q.+("status" -> JsString(f)))

    collection.find(finder).sort(Json.obj("created" -> -1)).cursor[ReportStatus]()
      .collect[Seq](-1, Cursor.FailOnError[Seq[ReportStatus]]())
      .flatMap { res =>
        Future.sequence(res.map(checkAndUpdateSubmissionStatus)).map(Right(_))
      }
      .recover {
        case ex: Throwable => {
          val errorMsg = s"Couldn't retrieve BA reports with '$baCode'"
          logger.warn(s"$errorMsg\n${ex.getMessage}")
          Left(BarMongoError(errorMsg))
        }
      }
  }

  override def getByReference(reference: String)
  : Future[Either[BarError, ReportStatus]] = {
    val finder = BSONDocument(_Id -> reference)
    collection.find(finder).sort(Json.obj("created" -> -1)).cursor[ReportStatus](ReadPreference.primary)
      .collect[Seq](1, Cursor.FailOnError[Seq[ReportStatus]]())
      .flatMap { res =>
        checkAndUpdateSubmissionStatus(res.head).map(Right(_))
      }
      .recover {
        case ex: Throwable => {
          val errorMsg = s"Couldn't retrieve BA reports for reference $reference"
          logger.warn(s"$errorMsg\n${ex.getMessage}")
          Left(BarMongoError(errorMsg))
        }
      }
  }

  override def getAll(): Future[Either[BarError, Seq[ReportStatus]]] = {
    collection.find(Json.obj()).sort(Json.obj("created" -> -1)).cursor[ReportStatus](ReadPreference.primary)
      .collect[Seq](-1, Cursor.FailOnError[Seq[ReportStatus]]())
      .flatMap { res =>
        Future.sequence(res.map(checkAndUpdateSubmissionStatus)).map(Right(_))
      }
      .recover {
        case ex: Throwable => {
          val errorMsg = s"Couldn't retrieve all BA reports"
          logger.warn(s"$errorMsg\n${ex.getMessage}")
          Left(BarMongoError(errorMsg))
        }
      }
  }

  protected def atomicSaveOrUpdate(reference: String, upsert: Boolean, finder: BSONDocument, modifierBson: BSONDocument) = {
    val updateDocument = if (upsert) {
      modifierBson ++ setOnInsert(BSONDocument(_Id -> reference))
    } else {
      modifierBson
    }
    val modifier = collection.updateModifier(updateDocument, upsert = upsert)
    collection.findAndModify(finder, modifier)
      .map(response => Either.cond(
        !response.lastError.isDefined || !response.lastError.get.err.isDefined,
        Unit,
        getError(response.lastError.get.err.get))
      )
      .recover {
        case ex: Throwable => {
          val errorMsg = "Error while saving submission"
          logger.error(errorMsg, ex)
          Left(BarMongoError(errorMsg))
        }
      }
  }

  private def getError(error: String): BarError = {
    val errorMsg = "Error while saving report status"
    logger.error(s"$errorMsg\n$error")
    BarMongoError(errorMsg)
  }


  def addErrors(submissionId: String, errors: List[Error]): Future[Either[BarError, Boolean]] = {
    val modifier = Json.obj(
      "$push" -> Json.obj(
        "errors" -> Json.obj(
          "$each" -> errors
        )
      )
    )

    collection.update(false).one(_id(submissionId), modifier, multi = true)

    collection.update(false).one(_id(submissionId), modifier).map { updateResult =>
      if (updateResult.ok && updateResult.n == 1) {
        Right(true)
      } else {
        Left(BarMongoError("unable record error message in mongo", Option(updateResult)))
      }
    }

  }

  override def addError(submissionId: String, error: Error): Future[Either[BarError, Boolean]] = {

    val modifier = BSONDocument(
      "$push" -> BSONDocument(
        "errors" -> error
      )
    )

    collection.update(ordered = false).one(_id(submissionId), modifier).map { updateResult =>
      if (updateResult.ok && updateResult.n == 1) {
        Right(true)
      } else {
        Left(BarMongoError("unable record error message in mongo", Option(updateResult)))
      }
    }
  }


  override def updateStatus(submissionId: String, status: ReportStatusType): Future[Either[BarError, Boolean]] = {

    val modifier = set(BSONDocument(
        "status" -> status.value
      )
    )

    collection.update.one(_id(submissionId), modifier, upsert = true, multi = false).map { updateResult =>

      if (updateResult.ok && updateResult.n == 1) {
        Right(true)
      } else {
        Left(BarMongoError("unable to update status in mongo", Option(updateResult)))
      }
    }
  }


  override def update(submissionId: String, status: ReportStatusType, totalReports: Int): Future[Either[BarError, Boolean]] = {
    val modifier = set(BSONDocument(
        "status" -> status.value,
        "totalReports" -> totalReports
      )
    )

    collection.update.one(_id(submissionId), modifier, upsert = true, multi = false).map { updateResult =>
      if (updateResult.ok && updateResult.n == 1) {
        Right(true)
      } else {
        Left(BarMongoError("unable to update status in mongo", Option(updateResult)))
      }
    }
  }

  override def deleteByReference(reference: String, user: String): Future[Either[BarError, JsValue]] = {
    val deleteSelector = Json.obj(_Id -> reference, "baCode" -> user)
    log.warn(s"Performing deletion on ${collectionName} with selector: ${deleteSelector}")
    collection.delete.one(deleteSelector).map { deleteResult =>
      val response = Json.obj(
        "code" -> deleteResult.code,
        "n" -> deleteResult.n,
        "writeErrors" -> deleteResult.writeErrors.mkString(","),
        "writeConcernError" -> deleteResult.writeConcernError.map(_.toString)
      )
      log.warn(s"Deletion on ${collectionName} done, returning response : ${response}")
      Right(response)
    }
  }

  def checkAndUpdateSubmissionStatus(report: ReportStatus): Future[ReportStatus] = {
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

  def markSubmissionFailed(report: ReportStatus): Future[ReportStatus] = {

    val q = Json.obj(
      "_id" -> report.id
    )
    val u = Json.obj(
      "$set" -> Json.obj(
        "status" -> Failed.value,
               "errors" -> Json.arr(Error(TIMEOUT_ERROR))
    ))

    val updatedReport: Future[ReportStatus] = collection
      .findAndUpdate(q, u, fetchNewObject = true, upsert = false)
      .flatMap{ updateResult =>
        val item: JsObject = updateResult.value.get
        ReportStatus.format.reads(item).fold(
          _ => Future.failed(new RuntimeException("xx")),
          Future.successful(_)
        )
      }

    updatedReport
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



