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

package uk.gov.hmrc.voabar.util

import org.mongodb.scala.model.Filters.{exists, not}
import play.api.Logging
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.voabar.repositories.SubmissionStatusRepository.submissionsCollectionName
import uk.gov.hmrc.voabar.repositories.{DefaultUserReportUploadsRepository, SubmissionStatusRepositoryImpl}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

/**
 * @author Yuriy Tumakha
 */
@Singleton
class DataMonitor @Inject()()(
  submissionStatusRepository: SubmissionStatusRepositoryImpl,
  userReportUploadsRepository: DefaultUserReportUploadsRepository
)(implicit ec: ExecutionContext) extends Logging {

  Future.sequence(Seq(submissionStatusRepository, userReportUploadsRepository)
    .map { repo =>
      for {
        count <- repo.collection.countDocuments().toFuture()
        countWithoutCreatedAt <- repo.collection.countDocuments(not(exists("createdAt"))).toFuture()
        countWithoutBACode <- repo.collection.countDocuments(not(exists("baCode"))).toFuture()
        countWithoutStatus <- repo.collection.countDocuments(not(exists("status"))).toFuture()
      } yield {
        val withoutCreatedAtStr = Option.when(countWithoutCreatedAt > 0)(countWithoutCreatedAt).fold("")(cnt => s" ($cnt - without `.createdAt`)")
        val withoutBACodeStr = Option.when(countWithoutBACode > 0 && isSubmissionsRepo(repo))(countWithoutBACode).fold("")(cnt => s" ($cnt - without `.baCode`)")
        val withoutStatusStr = Option.when(countWithoutStatus > 0 && isSubmissionsRepo(repo))(countWithoutStatus).fold("")(cnt => s" ($cnt - without `.status`)")
        s"collection '${repo.collectionName}': $count$withoutCreatedAtStr$withoutBACodeStr$withoutStatusStr"
      }
    }).map(messages => logger.warn(messages.mkString(" \n")))

  private def isSubmissionsRepo(repo: PlayMongoRepository[?]): Boolean =
    repo.collectionName == submissionsCollectionName

}
