/*
 * Copyright 2024 HM Revenue & Customs
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

import ebars.xml.BAreports

/**
 * Created by rgallet on 16/02/16.
 *
 * Monad style baby!
 */

sealed trait ExceptionsAccumulator[A <: ReportErrorDetail, B <: BAreports] {

  def get: Seq[ReportErrorDetail]

  def map(f: B => Option[A]): ExceptionsAccumulator[A, B]

  def flatMap(f: B => ExceptionsAccumulator[A, B]): ExceptionsAccumulator[A, B]
}

final case class EmptyReportValidation[A <: ReportErrorDetail, B <: BAreports]() extends ExceptionsAccumulator[A, B] {

  override def get: Seq[ReportErrorDetail] = Seq.empty[ReportErrorDetail]

  override def flatMap(f: (B) => ExceptionsAccumulator[A, B]): ExceptionsAccumulator[A, B] = EmptyReportValidation()

  override def map(f: (B) => Option[A]): ExceptionsAccumulator[A, B] = EmptyReportValidation()
}

final case class ReportValidation[A <: ReportErrorDetail, B <: BAreports](errors: Seq[A], report: B) extends ExceptionsAccumulator[A, B] {

  override def get: Seq[ReportErrorDetail] = errors

  override def map(f: (B) => Option[A]): ExceptionsAccumulator[A, B] =
    f(report) match {
      case Some(newErrors) => copy(errors = errors :+ newErrors)
      case _               => this
    }

  override def flatMap(f: (B) => ExceptionsAccumulator[A, B]): ExceptionsAccumulator[A, B] = f(report)
}
