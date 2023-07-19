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

package uk.gov.hmrc.voabar.services

import java.io.{InputStream, Reader}

import org.w3c.dom.ls.LSInput

class Input extends LSInput {
  private var systemId, baseURI, publicId: String = _

  private var reader: Reader = _
  private var byteStream: InputStream = _
  private var certifiedText: Boolean = _
  private var encoding, stringData: String = _

  override def getBaseURI: String = baseURI

  override def setBaseURI(baseURI: String): Unit = this.baseURI = baseURI

  override def setByteStream(byteStream: InputStream): Unit = this.byteStream = byteStream

  override def getCharacterStream: Reader = reader

  override def setCertifiedText(certifiedText: Boolean) = this.certifiedText = certifiedText

  override def getEncoding: String = encoding

  override def setEncoding(encoding: String) = this.encoding = encoding

  override def getStringData: String = stringData

  override def getSystemId: String = systemId

  override def getByteStream: InputStream = byteStream

  override def getPublicId: String = publicId

  override def setPublicId(publicId: String): Unit = this.publicId = publicId

  override def setSystemId(systemId: String): Unit = this.systemId = systemId

  override def getCertifiedText: Boolean = certifiedText

  override def setCharacterStream(characterStream: Reader) = this.reader = characterStream

  override def setStringData(stringData: String) = this.stringData = stringData
}
