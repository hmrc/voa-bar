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

import java.time.{Instant, LocalDate, ZoneId}
import java.util.GregorianCalendar

import javax.xml.datatype.{DatatypeFactory, XMLGregorianCalendar}

object DateConversion {

  val LONDON_ZONE = ZoneId.of("Europe/London")

  implicit class LocalDateToXmlGregorianCalendar(localDate: LocalDate) {
    def toXml(implicit df: DatatypeFactory): XMLGregorianCalendar = {
      df.newXMLGregorianCalendarDate(localDate.getYear, localDate.getMonthValue, localDate.getDayOfMonth, javax.xml.datatype.DatatypeConstants.FIELD_UNDEFINED)
    }
  }

  implicit class InstantToXmlGregorianCalendar(instant: Instant) {
    def toXml(implicit df: DatatypeFactory): XMLGregorianCalendar = {
      df.newXMLGregorianCalendar(GregorianCalendar.from(instant.atZone(LONDON_ZONE)))
    }
  }

}
