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
import uk.gov.hmrc.voabar.repositories.SubmissionStatusRepositoryImpl
import uk.gov.hmrc.voabar.util.PlayMongoUtil.byId

import java.time.ZonedDateTime
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
                                                mongoLockRepository: MongoLockRepository
                                              )(implicit ec: ExecutionContext, actorSystem: ActorSystem) extends Logging {

  private val collection = submissionStatusRepository.collection
  private val daysToRemove = 92
  private val dateToRemove = ZonedDateTime.now.minusDays(daysToRemove)
  private val lockService = LockService(mongoLockRepository, lockId = "CreatedDateMigrationLock", ttl = 12 hours)

  actorSystem.scheduler.scheduleOnce(30 seconds) {
    runWithLock()
  }

  private def printCounts(): Unit = {
    count("submissions", Document())
    count("submissions.created", exists("created"))
    count("submissions.createdAt", exists("createdAt"))
  }

  private def migrate(): Future[Long] = {
    val source: Source[ReportStatus, NotUsed] =
      Source.fromPublisher(
        collection
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
    collection.updateOne(byId(reportStatus.id), modifier).toFutureOption()
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

  private def count(label: String, filter: Bson): Unit = {
    val total = Await.result(collection.countDocuments(filter).toFuture(), 9 seconds)
    logger.warn(s"$label: $total")
  }

  def runWithLock(): Future[Long] = {
    lockService.withLock {
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

    printCounts()

    migrate()
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

}
