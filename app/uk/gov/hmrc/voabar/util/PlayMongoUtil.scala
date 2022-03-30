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

package uk.gov.hmrc.voabar.util

import com.typesafe.config.ConfigException
import org.mongodb.scala.model.IndexOptions
import play.api.Configuration

import java.util.concurrent.TimeUnit

/**
 * @author Yuriy Tumakha
 */
object PlayMongoUtil {

  def indexOptionsWithTTL(indexName: String, collectionName: String, config: Configuration): IndexOptions =
    IndexOptions().name(indexName)
      .expireAfter(ttlSeconds(collectionName, config), TimeUnit.SECONDS)

  private def ttlSeconds(collectionName: String, config: Configuration): Long = {
    val ttlPath = s"$collectionName.timeToLiveInSeconds"
    config.getOptional[Int](ttlPath)
      .getOrElse(throw new ConfigException.Missing(ttlPath))
  }

}
