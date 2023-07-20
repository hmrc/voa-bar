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

import cats.MonadThrow
import cats.effect.IO
import cats.effect.unsafe.implicits._
import cats.implicits._
import play.api.Logging
import play.api.mvc.Result
import play.api.mvc.Results.InternalServerError
import uk.gov.hmrc.voabar.exception.VoaBarException

import scala.concurrent.Future

/**
 * @author Yuriy Tumakha
 */
trait FunctionalRun extends Logging {

  type F[A] = IO[A]

  val F: MonadThrow[F] = MonadThrow[F]

  def f[A](a: A): F[A] = F.pure(a)

  def fromFuture[A](future: Future[A]): F[A] =
    IO.fromFuture(IO(future))

  def run(f: F[Result]): Future[Result] =
    f.recover {
      case voaBarException: VoaBarException =>
        logger.error(s"VoaBarException: ${voaBarException.error}")
        InternalServerError(voaBarException.getMessage)
      case ex =>
        logger.error(s"Exception: $ex")
        InternalServerError(ex.getMessage)
    }.unsafeToFuture()

}
