/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.voabar.services

import akka.actor.{ActorSystem, Scheduler}
import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}
import play.api.Logger
import uk.gov.hmrc.voabar.models.{Done, Verified}
import uk.gov.hmrc.voabar.repositories.SubmissionStatusRepository

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

@ImplementedBy(classOf[DefaultWebBarsService])
trait WebBarsService {
  def newSubmission(id: String): Unit
}

@Singleton
class DefaultWebBarsService @Inject() (actorSystem: ActorSystem, submissionRepository: SubmissionStatusRepository)(
  implicit ec: ExecutionContext) extends WebBarsService {

  val log = Logger(this.getClass)

  override def newSubmission(id: String): Unit = {
    log.debug(s"New WebBars report scheduled : ${id}")
    actorSystem.scheduler.scheduleOnce(10 seconds) {
      val update = submissionRepository.updateStatus(id, Verified)
      update.onComplete { _ =>
        log.debug(s"WebBars report ${id} updated to ${Verified}")
        actorSystem.scheduler.scheduleOnce(10 seconds) {
          submissionRepository.updateStatus(id, Done).onComplete { _ =>
            log.debug(s"WebBars report ${id} updated to ${Done}")
          }
        }
      }
    }
  }

}
