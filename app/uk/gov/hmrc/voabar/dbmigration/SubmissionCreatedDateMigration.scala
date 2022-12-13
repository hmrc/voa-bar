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

package uk.gov.hmrc.voabar.dbmigration

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import org.mongodb.scala.Document
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.{exists, not}
import org.mongodb.scala.model.Projections.{fields, include}
import org.mongodb.scala.model.Updates
import org.mongodb.scala.model.Updates.set
import play.api.Logging
import uk.gov.hmrc.mongo.lock.{LockService, MongoLockRepository}
import uk.gov.hmrc.voabar.models.ReportStatus
import uk.gov.hmrc.voabar.repositories.{DefaultUserReportUploadsRepository, SubmissionStatusRepositoryImpl}
import uk.gov.hmrc.voabar.util.PlayMongoUtil.byId

import java.time.temporal.ChronoUnit
import java.time.{Instant, ZonedDateTime}
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps

/**
 * Copy `submissions.created` as string and save to property `submissions.createdAt` in ISO Date format.
 *
 * <pre>
 * Example:
 * "created" : "2022-11-26T20:55:13.080+01:00[Europe/London]"
 * "createdAt" : {"&#36;date" : {"&#36;numberLong" : "1669496113080"}}
 * </pre>
 *
 * @author Yuriy Tumakha
 */
@Singleton
class SubmissionCreatedDateMigration @Inject()(
                                                submissionStatusRepository: SubmissionStatusRepositoryImpl,
                                                userReportUploadsRepository: DefaultUserReportUploadsRepository,
                                                mongoLockRepository: MongoLockRepository
                                              )(implicit ec: ExecutionContext, actorSystem: ActorSystem) extends Logging {

  private val submissions = submissionStatusRepository.collection
  private val userReportUpload = userReportUploadsRepository.collection
  private val daysToRemove = 92
  private val dateToRemove = ZonedDateTime.now.minusDays(daysToRemove)
  private val submissionsLockService = LockService(mongoLockRepository, lockId = "CreatedDateMigrationSubmissionsLock", ttl = 12 hours)
  private val reportUploadLockService = LockService(mongoLockRepository, lockId = "SetCreatedAtReportUploadLock", ttl = 2 hours)

  actorSystem.scheduler.scheduleOnce(30 seconds) {
    runWithLockSubmissions()
    runWithLockReportUpload()
  }

  private def printSubmissionsCounts(): Unit = {
    countSubmissions("submissions", Document())
    countSubmissions("submissions.created", exists("created"))
    countSubmissions("submissions.createdAt", exists("createdAt"))
  }

  private def migrateSubmissionsCreatedDate(): Future[Long] = {
    val source: Source[ReportStatus, NotUsed] =
      Source.fromPublisher(
        submissions
          .find(not(exists("createdAt")))
          .projection(fields(include("created")))
      )

    source
      .mapAsync(2)(saveCreatedAtData)
      .runWith(Sink.fold(0L)(_ + _))
  }

  private def saveCreatedAtData(reportStatus: ReportStatus): Future[Long] = {
    val created = reportStatus.created.getOrElse(dateToRemove)

    val modifier = Updates.combine(
      set("createdAt", created.toInstant)
    )
    submissions.updateOne(byId(reportStatus.id), modifier).toFutureOption()
      .map {
        case Some(updateResult) => updateResult.getModifiedCount
        case _ => 0L
      }
      .recover {
        case ex: Throwable =>
          logger.error(s"Unable to update 'createdAt' for ${reportStatus.id}", ex)
          0L
      }
  }

  private def countSubmissions(label: String, filter: Bson): Unit = {
    val total = Await.result(submissions.countDocuments(filter).toFuture(), 9 seconds)
    logger.warn(s"$label: $total")
  }

  def runWithLockSubmissions(): Future[Long] = {
    submissionsLockService.withLock {
      run()
    }.map {
      case Some(res) =>
        logger.warn(s"CreatedDateMigration task completed. $res")
        res
      case None =>
        logger.warn("CreatedDateMigration task already started on another instance")
        0L
    }
  }

  def run(): Future[Long] = {
    logger.warn(s"Convert submissions `created` to `createdAt` started at ${ZonedDateTime.now}")

    printSubmissionsCounts()

    migrateSubmissionsCreatedDate()
      .recoverWith {
        case e: Exception =>
          logger.error(s"Error on migration: ${e.getMessage}", e)
          Future.failed(e)
      }
      .map(updatedCount => {
        logger.info(s"Completed converting `submissions.created` for $updatedCount records")
        logger.warn(s"Convert submissions `created` to `createdAt` completed at ${ZonedDateTime.now}")
        updatedCount
      })
  }

  def runWithLockReportUpload(): Future[Long] = {
    reportUploadLockService.withLock {
      setCreatedAtIfEmpty()
    }.map {
      case Some(res) =>
        logger.warn(s"SetCreatedAtReportUpload query completed. $res")
        res
      case None =>
        logger.warn("SetCreatedAtReportUpload query already started by another instance")
        0L
    }
  }

  private def countReportUpload(label: String, filter: Bson): Unit = {
    val total = Await.result(userReportUpload.countDocuments(filter).toFuture(), 8 seconds)
    logger.warn(s"$label: $total")
  }

  def setCreatedAtIfEmpty(): Future[Long] = {
    logger.warn(s"Run Mongo Query 'setCreatedAtIfEmpty' at ${ZonedDateTime.now}")

    countReportUpload("userreportupload", Document())
    countReportUpload("userreportupload.lastUpdated", exists("lastUpdated"))
    countReportUpload("userreportupload.createdAt", exists("createdAt"))

    val now = Instant ofEpochMilli Instant.now.toEpochMilli
    val removeAt = now.plus(3, ChronoUnit.HOURS)

    userReportUpload
      .updateMany(
        not(exists("createdAt")),
        set("createdAt", removeAt)
      )
      .toFuture()
      .map(_.getModifiedCount)
  }

}
