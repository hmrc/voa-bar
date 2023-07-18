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

import ebars.xml.BApropertySplitMergeStructure.AssessmentProperties
import ebars.xml.BAreportBodyStructure.TypeOfTax
import ebars.xml._
import jakarta.xml.bind.JAXBElement
import javax.xml.namespace.QName
import models.Purpose

import scala.jdk.CollectionConverters._

/**
  * Created by rgallet on 12/02/16.
  *
  * Note: only in use for BAreports containing 1 and only 1 report. Subsequent entries within same BAreports would be ignored.
  */
object EbarsXmlCutter {

  /**
    * Returns tjhe first report from BAreports. BAreports is expected to only hold one report.
    *
    * @param bAreports the XML report
    * @return
    */
  private def content(bAreports: BAreports) = bAreports.getBApropertyReport.get(0).getContent

  /**
    * Extracts CR code value
    *
    * @param bAreports the XML report
    * @return optional CR code enum CtaxReasonForReportCodeContentType
    */
  def extractCR(bAreports: BAreports) = {
    import models.EbarsBAreports._

    (bAreports.purpose: @unchecked) match {
      case Purpose.CT =>
        content(bAreports).asScala.find(e => e.getName.getLocalPart == "TypeOfTax" && !e.isNil)
          .flatMap(e => Option(e.getValue.asInstanceOf[TypeOfTax]))
          .flatMap(e => Option(e.getCtaxReasonForReport))
          .map(e => e.getReasonForReportCode)
          .map(e => e.getValue).filter(_ != null)
      case Purpose.NDR =>
        content(bAreports).asScala.find(e => e.getName.getLocalPart == "TypeOfTax")
          .map(e => e.getValue.asInstanceOf[TypeOfTax])
          .map(e => e.getNNDRreasonForReport)
          .map(e => e.getReasonForReportCode)
          .filter(_ != null)
          .map(e => e.getValue).filter(_ != "")
    }
  }

  /**
    * Returns <AssessmentProperties> elements from both <ExistingEntries> and <ProposedEntries>
    *
    * @param bAreports the XML report
    * @return Seq of ebars.xml.AssessmentProperties
    */
  def getAssessmentProperties(bAreports: BAreports) = {
    val proposedEntries = findProposedEntriesIdx(bAreports)
    val existingEntries = findExistingEntriesIdx(bAreports)

    val propertyEntries = (existingEntries ++: proposedEntries)

    val properties = propertyEntries map (idx => content(bAreports).get(idx))

    properties.map(_.getValue.asInstanceOf[BApropertySplitMergeStructure]).flatMap(_.getAssessmentProperties.asScala)
  }

  /**
    * Returns <CurrentTax> elements from all <AssessmentProperties> in both <ExistingEntries> and <ProposedEntries>
    *
    * @param bAreports the XML report
    * @return Seq of ebars.xml.CurrentTax
    */
  def getCurrentTaxes(bAreports: BAreports) = getAssessmentProperties(bAreports) map (_.getCurrentTax) filterNot (_ == null)


  /**
    * Removes all <CurrentTax>  elements from all <AssessmentProperties> in both <ExistingEntries> and <ProposedEntries>
    * which are either empty or NULL
    *
    * @param bAreports the XML report
    */
  def removeNullCurrentTax(bAreports: BAreports) =
    getAssessmentProperties(bAreports) foreach { assessmentProperties =>
      assessmentProperties.getCurrentTax match {
        case null => //nothing
        case currentTax if currentTax.getCouncilTaxBand == null => assessmentProperties.setCurrentTax(null)
        case _ => //nothing
      }
    }

  /**
    * Returns <PropertyIdentity> elements from all <AssessmentProperties> in both <ExistingEntries> and <ProposedEntries>
    *
    * @param bAreports the XML report
    * @return Seq of ebars.xml.BApropertyIdentificationStructure
    */
  def getPropertyIdentities(bAreports: BAreports) = getAssessmentProperties(bAreports) map (_.getPropertyIdentity)

  /**
    * Returns <PropertyDescription> elements from all <AssessmentProperties> in both <ExistingEntries> and <ProposedEntries>
    *
    * @param bAreports the XML report
    * @return Seq of ebars.xml.PropertyDescription
    */
  def getPropertyDescriptions(bAreports: BAreports) = getAssessmentProperties(bAreports) map (_.getPropertyDescription) filterNot (_ == null)

  /**
    * Returns <OccupierContact> elements from all <AssessmentProperties> in both <ExistingEntries> and <ProposedEntries>
    *
    * @param bAreports the XML report
    * @return Seq of ebars.xml.OccupierContactStructure
    */
  def getOccupierContacts(bAreports: BAreports): Seq[OccupierContactStructure] = getAssessmentProperties(bAreports).flatMap(getOccupierContacts)

  def getOccupierContacts(assessmentProperties: AssessmentProperties): Option[OccupierContactStructure] =
    assessmentProperties.getOccupierContact match {
      case null => None
      case v => Some(v)
    }

  /**
    *
    * Removes BS7666Address elements from XML
    *
    * @param bAreports the XML report
    */
  def removeBS7666Address(bAreports: BAreports) = {
    val propertyIdentities = getPropertyIdentities(bAreports)

    propertyIdentities foreach { bApropertyIdentificationStructure =>
      bApropertyIdentificationStructure.getContent.asScala.zipWithIndex find (_._1.getName.getLocalPart == "BS7666Address") map (_._2) match {
        case Some(index) => bApropertyIdentificationStructure.getContent.remove(index)
        case _ => //nothing
      }
    }
  }

  /**
    *
    * Removes PropertyGridCoords elements from XML
    *
    * @param bAreports the XML report
    */
  def removePropertyGridCoords(bAreports: BAreports) = {
    val propertyIdentities = getPropertyIdentities(bAreports)

    propertyIdentities foreach { bApropertyIdentificationStructure =>
      bApropertyIdentificationStructure.getContent.asScala.zipWithIndex find (_._1.getName.getLocalPart == "PropertyGridCoords") map (_._2) match {
        case Some(index) => bApropertyIdentificationStructure.getContent.remove(index)
        case _ => //nothing
      }
    }
  }

  /**
    * Returns <TextAddress> elements from all <AssessmentProperties> in both <ExistingEntries> and <ProposedEntries>
    *
    * @param bAreports the XML report
    * @return Seq of ebars.xml.TextAddressStructure
    */
  def getTextAddressStructures(bAreports: BAreports): Seq[TextAddressStructure] = getPropertyIdentities(bAreports) flatMap getTextAddressStructures

  /**
    * Returns <TextAddress> elements from all <AssessmentProperties> in both <ExistingEntries> and <ProposedEntries>
    *
    * @param baPropertyIdentificationStructure the BApropertyIdentificationStructure <PropertyIdentity> from the XML report
    * @return Seq of ebars.xml.TextAddressStructure
    */
  def getTextAddressStructures(baPropertyIdentificationStructure: BApropertyIdentificationStructure): Seq[TextAddressStructure] =
    baPropertyIdentificationStructure.getContent.asScala.filter(_.getName.getLocalPart == "TextAddress")
      .map(_.getValue.asInstanceOf[TextAddressStructure]).toSeq

  /**
    * Returns <BAreference> elements from all <AssessmentProperties> in both <ExistingEntries> and <ProposedEntries>
    *
    * @param bAreports the XML report
    * @return Seq of Strings
    */
  def getBAreferences(bAreports: BAreports): Seq[String] = getPropertyIdentities(bAreports) flatMap getBAreferences

  /**
    * Returns <BAreference> elements from all <AssessmentProperties> in both <ExistingEntries> and <ProposedEntries>
    *
    * @param baPropertyIdentificationStructure the BApropertyIdentificationStructure <PropertyIdentity> from the XML report
    * @return Seq of Strings
    */
  def getBAreferences(baPropertyIdentificationStructure: BApropertyIdentificationStructure): Seq[String] =
    baPropertyIdentificationStructure.getContent.asScala.filter(_.getName.getLocalPart == "BAreference").map(_.getValue.asInstanceOf[String]).toSeq

  /**
    * Returns <OccupierContact> elements from all <AssessmentProperties> in both <ExistingEntries> and <ProposedEntries>
    *
    * @param bAreports the XML report
    * @return Seq of ebars.xml.UKPostalAddressStructure
    */
  def getOccupierContactAddresses(bAreports: BAreports) = {
    val occupierContactAddresses = getOccupierContacts(bAreports)
    occupierContactAddresses map (_.getContactAddress) filterNot (_ == null)
  }

  /**
    * Returns the <Remarks> element in <BApropertyReport>
    *
    * Not the ones in <AssessmentProperties>
    *
    * @param bAreports the XML report
    * @return Option of String
    */
  def getRemarks(bAreports: BAreports) =
    EbarsXmlCutter.findRemarksIdx(bAreports).headOption map { idx =>
      val content = bAreports.getBApropertyReport.get(0).getContent
      val remarks = content.get(idx)
      remarks.getValue.asInstanceOf[String]
    }

  /**
    * Returns the <PropertyPlanReferenceNumber> element in <BApropertyReport>
    *
    * @param bAreports the XML report
    * @return Option of String
    */
  def getPropertyPlanReferenceNumber(bAreports: BAreports) =
    EbarsXmlCutter.findPropertyPlanReferenceNumberIdx(bAreports) map { idx =>
      val content = bAreports.getBApropertyReport.get(0).getContent
      val remarks = content.get(idx)
      remarks.getValue.asInstanceOf[String]
    }

  /**
    * Returns, if any, all indices of elements whose QName's LocalPart is @name
    *
    * Only 1-level deep. Only looks up elements right underneath <BApropertyReport>
    *
    * @param name      The qname value to look up
    * @param bAreports the XML report
    * @return
    */
  private def findEntriesIdx(name: String)(bAreports: BAreports): Seq[Int] =
    content(bAreports).asScala.zipWithIndex.filter(e => e._1.getName.getLocalPart == name).map(e => e._2).toSeq

  def findTypeOfTaxIdx = findEntriesIdx("TypeOfTax") _

  def findRemarksIdx = findEntriesIdx("Remarks") _

  def findPropertyPlanReferenceNumberIdx = findEntriesIdx("PropertyPlanReferenceNumber") _

  def findLastTypeOfTaxIdx(bAreports: BAreports) = findTypeOfTaxIdx(bAreports).reverse.headOption

  def findExistingEntriesIdx = findEntriesIdx("ExistingEntries") _

  def findProposedEntriesIdx = findEntriesIdx("ProposedEntries") _

  def findFirstExistingEntriesIdx(bAreports: BAreports) = findExistingEntriesIdx(bAreports).headOption

  def findFirstProposedEntriesIdx(bAreports: BAreports) = findProposedEntriesIdx(bAreports).headOption

  /**
    * Removes <ProposedEntries>
    *
    * @param bAreports the XML report
    */
  def removeProposedEntries(bAreports: BAreports): Unit = {
    val indices = findProposedEntriesIdx(bAreports)

    indices foreach content(bAreports).remove
  }

  /**
    * Removes <ExistingEntries>
    *
    * @param bAreports the XML report
    */
  def removeExistingEntries(bAreports: BAreports): Unit = {
    val indices = findExistingEntriesIdx(bAreports)

    indices foreach content(bAreports).remove
  }

  /**
    * Performs a shallow copy of all <ExistingEntries>/<AssessmentProperties> into <ProposedEntries>
    *
    * Copy is shallow in that only <TextAddress> is copied over.
    * Existing <ProposedEntries> are removed.
    *
    * @param bAreports the XML report
    */
  def copyExistingEntriesToProposed(bAreports: BAreports) = {
    removeProposedEntries(bAreports)

    findFirstExistingEntriesIdx(bAreports) foreach { index =>
      val existingPropertiesValue = content(bAreports).get(index).getValue.asInstanceOf[BApropertySplitMergeStructure] //existing entry's data

      val proposedPropertiesValue = new BApropertySplitMergeStructure //the destination <ProposedEntries>/<AssessmentProperties>

      existingPropertiesValue.getAssessmentProperties.asScala foreach { assessmentProperties =>
        val existingTextAddressStructures = getTextAddressStructures(assessmentProperties.getPropertyIdentity)
        val baReferences = getBAreferences(assessmentProperties.getPropertyIdentity)

        val copyPropertyIdentity = new BApropertyIdentificationStructure
        val copyAssessmentProperty = new AssessmentProperties
        copyAssessmentProperty.setPropertyIdentity(copyPropertyIdentity)
        proposedPropertiesValue.getAssessmentProperties.add(copyAssessmentProperty)

        existingTextAddressStructures.zipWithIndex foreach { case (existingTextAddressStructure, i) =>
          val textAddressStructureCopy = new TextAddressStructure

          existingTextAddressStructure.getAddressLine.asScala foreach (textAddressStructureCopy.getAddressLine.add)

          textAddressStructureCopy.setPostcode(existingTextAddressStructure.getPostcode)

          copyPropertyIdentity.getContent.add(createTextAddress(textAddressStructureCopy))

          /**
            * In theory, each <TextAddress> would be followed by a <BAreference> as per XSDs.
            * So baReferences(i) should always be there. Nevertheless, the initially submitted
            * XML may be invalid in the sense that <BAreference> is missing, hence the check.
            */
          if (baReferences.isDefinedAt(i)) {
            copyPropertyIdentity.getContent.add(createBAreference(baReferences(i)))
          }
        }
      }

      /**
        * index is that of <ExistingEntries>. <ProposedEntries> must be the element right after as per XSDs.
        */
      content(bAreports).add(index + 1, createProposedEntries(proposedPropertiesValue))
    }
  }

  /**
    * Performs a shallow copy of all <ProposedEntries>/<AssessmentProperties> into <ExistingEntries>
    *
    * Copy is shallow in that only <TextAddress> is copied over.
    * Existing <ExistingEntries> are removed.
    *
    * @param bAreports the XML report
    */
  def copyProposedEntriesToExisting(bAreports: BAreports): Unit = {
    removeExistingEntries(bAreports)

    findFirstProposedEntriesIdx(bAreports) foreach { index =>
      val proposedPropertiesValue = content(bAreports).get(index).getValue.asInstanceOf[BApropertySplitMergeStructure] //existing entry's data

      val existingPropertiesValue = new BApropertySplitMergeStructure

      proposedPropertiesValue.getAssessmentProperties.asScala foreach { assessmentProperties =>
        val proposedTextAddressStructures = getTextAddressStructures(assessmentProperties.getPropertyIdentity)
        val baReferences = getBAreferences(assessmentProperties.getPropertyIdentity)

        val copyPropertyIdentity = new BApropertyIdentificationStructure
        val copyAssessmentProperty = new AssessmentProperties
        copyAssessmentProperty.setPropertyIdentity(copyPropertyIdentity)
        existingPropertiesValue.getAssessmentProperties.add(copyAssessmentProperty)

        proposedTextAddressStructures.zipWithIndex foreach { case (proposedTextAddressStructure, i) =>
          val textAddressStructureCopy = new TextAddressStructure

          proposedTextAddressStructure.getAddressLine.asScala foreach (textAddressStructureCopy.getAddressLine.add)

          textAddressStructureCopy.setPostcode(proposedTextAddressStructure.getPostcode)

          copyPropertyIdentity.getContent.add(createTextAddress(textAddressStructureCopy))
          if (baReferences.isDefinedAt(i)) {
            copyPropertyIdentity.getContent.add(createBAreference(baReferences(i)))
          }
        }
      }

      /**
        * index is that of <ProposedEntries>. <ExistingEntries> must be the element right before as per XSDs.
        */
      content(bAreports).add(index, createExistingEntries(existingPropertiesValue))
    }
  }

  private def createProposedEntries(baPropertySplitMergeStructure: BApropertySplitMergeStructure) = {
    val qName = new QName("http://www.govtalk.gov.uk/LG/Valuebill", "ProposedEntries")
    new JAXBElement(qName, classOf[BApropertySplitMergeStructure], classOf[BApropertyIdentificationStructure], baPropertySplitMergeStructure)
  }

  private def createExistingEntries(baPropertySplitMergeStructure: BApropertySplitMergeStructure) = {
    val qName = new QName("http://www.govtalk.gov.uk/LG/Valuebill", "ExistingEntries")
    new JAXBElement(qName, classOf[BApropertySplitMergeStructure], classOf[BApropertyIdentificationStructure], baPropertySplitMergeStructure)
  }

  private def createTextAddress(textAddressStructure: TextAddressStructure) = {
    val qName = new QName("http://www.govtalk.gov.uk/LG/Valuebill", "TextAddress")
    new JAXBElement(qName, classOf[TextAddressStructure], classOf[BApropertyIdentificationStructure], textAddressStructure)
  }

  private def createBAreference(baReference: String) = {
    val qName = new QName("http://www.govtalk.gov.uk/LG/Valuebill", "BAreference")
    new JAXBElement(qName, classOf[String], classOf[BApropertyIdentificationStructure], baReference)
  }

  /**
    * Loops through all <ProposedEntries>/<AssessmentProperties>, edits the <TextAddress>/<AddressLine> elements
    * in each by prefix the value @prefix. And adds a reference for each <ProposedEntries>/<AssessmentProperties> into
    * <ExistingProperties>.
    *
    * Note: this is not a copy at all. <ProposedEntries>/<AssessmentProperties> are not duplicated, merely referenced twice
    * in both <ProposedEntries> and <ExistingEntries>.
    *
    * @param bAreports the XML report
    * @param prefix    The value that serves as a prefix.
    */
  def appendProposedEntriesToExisting(bAreports: BAreports, prefix: String = "[PROPOSED] "): Unit = {
    val existingPropertiesValue = findFirstExistingEntriesIdx(bAreports) match {
      case Some(index) => content(bAreports).get(index).getValue.asInstanceOf[BApropertySplitMergeStructure]
      case None =>
        val newExistingPropertiesValue = new BApropertySplitMergeStructure
        findLastTypeOfTaxIdx(bAreports) foreach { typeOfTaxIndex =>
          content(bAreports).add(typeOfTaxIndex, createExistingEntries(newExistingPropertiesValue))
        }

        newExistingPropertiesValue
    }

    findFirstProposedEntriesIdx(bAreports) foreach { index =>
      val proposedPropertiesValue = content(bAreports).get(index).getValue.asInstanceOf[BApropertySplitMergeStructure] //proposed entry's data

      proposedPropertiesValue.getAssessmentProperties.asScala foreach { assessmentProperties =>
        val proposedTextAddressStructures = getTextAddressStructures(assessmentProperties.getPropertyIdentity)
        val baReferences = getBAreferences(assessmentProperties.getPropertyIdentity)

        val copyPropertyIdentity = new BApropertyIdentificationStructure
        val copyAssessmentProperty = new AssessmentProperties
        copyAssessmentProperty.setPropertyIdentity(copyPropertyIdentity)

        existingPropertiesValue.getAssessmentProperties.add(copyAssessmentProperty)

        proposedTextAddressStructures.zipWithIndex foreach { case (proposedTextAddressStructure, i) =>
          val textAddressStructureCopy = new TextAddressStructure

          //TODO - What is address is too long
          // should we trip proposet od addres?? I don't know
          proposedTextAddressStructure.getAddressLine.asScala map (prefix + _) foreach (textAddressStructureCopy.getAddressLine.add)


          textAddressStructureCopy.setPostcode(proposedTextAddressStructure.getPostcode)

          copyPropertyIdentity.getContent.add(createTextAddress(textAddressStructureCopy))
          if (baReferences.isDefinedAt(i)) {
            copyPropertyIdentity.getContent.add(createBAreference(baReferences(i)))
          }
        }
      }
    }
  }

  /**
    * Loops through all <ProposedEntries>/<AssessmentProperties>, edits the <TextAddress>/<AddressLine> elements
    * in each by prefix the value @prefix. Appends all <TextAddress>/<AddressLine> and <TextAddress>/<Postcode> to
    * existing <Remarks> element.
    *
    * @param bAreports the XML report
    * @param prefix    The value that serves as a prefix.
    */
  def appendProposedEntriesToRemarks(bAreports: BAreports, prefix: String = "[PROPOSED] "): Unit = {
    val existingRemarks = getRemarks(bAreports) //current Remarks value

    //all <ProposedEntries>/<AssessmentProperties>
    val g = findFirstProposedEntriesIdx(bAreports) match {
      case Some(proposedEntriesIndex) =>
        content(bAreports).get(proposedEntriesIndex).getValue.asInstanceOf[BApropertySplitMergeStructure].getAssessmentProperties.asScala.toList
      case _ => Nil
    }

    //producing a []-enclosed value with AddressLine and Postcode
    val addressLines = g map (_.getPropertyIdentity) filterNot (_ == null) flatMap (getTextAddressStructures) map { textAddressStructure =>
      val addressLines = textAddressStructure.getAddressLine.asScala.mkString(",").trim
      s"[$addressLines,${textAddressStructure.getPostcode}]"
    }

    findRemarksIdx(bAreports) map content(bAreports).remove //removing existing remarks element

    //creating and adding a new <Remarks> element
    val proposedQName = new QName(content(bAreports).get(0).getName.getNamespaceURI, "Remarks")
    val newRemarksValue = existingRemarks.map(_ + " - ").getOrElse("") + s"$prefix- " + addressLines.mkString(",").trim //TODO - What if remarks are too long?
                                                                                                                        // should we remove rest of address?
                                                                                                                        // fix bug in ebars
    val newRemarks = new JAXBElement(proposedQName, classOf[String], classOf[BAreportBodyStructure], newRemarksValue)
    content(bAreports).add(newRemarks) //Remarks element must be last
  }

  /**
    * Converts <ExistingEntries> into <ProposedEntries>
    *
    * Will remove any pre-existing ProposedEntries element first.
    *
    * @param bAreports the report
    */
  def convertExistingEntriesIntoProposedEntries(bAreports: BAreports): Unit = {
    removeProposedEntries(bAreports)

    findFirstExistingEntriesIdx(bAreports) foreach { index =>
      val value = content(bAreports).get(index).getValue.asInstanceOf[BApropertySplitMergeStructure] //existing entry's data

      val newBApropertySplitMergeStructure = createProposedEntries(value)

      removeExistingEntries(bAreports)

      findLastTypeOfTaxIdx(bAreports) foreach { typeOfTaxIndex =>
        content(bAreports).add(typeOfTaxIndex + 1, newBApropertySplitMergeStructure)
      }
    }
  }

  /**
    * Converts <ProposedEntries> into <ExistingEntries>
    *
    * ill remove any pre-existing ExistingEntries element first.
    *
    * @param bAreports
    */
  def convertProposedEntriesIntoExistingEntries(bAreports: BAreports): Unit = {
    removeExistingEntries(bAreports)

    findFirstProposedEntriesIdx(bAreports) foreach { index =>
      val value = content(bAreports).get(index).getValue.asInstanceOf[BApropertySplitMergeStructure] //existing entry's data

      val newBApropertySplitMergeStructure = createExistingEntries(value)

      removeProposedEntries(bAreports)

      findLastTypeOfTaxIdx(bAreports) foreach { typeOfTaxIndex =>
        content(bAreports).add(typeOfTaxIndex + 1, newBApropertySplitMergeStructure)
      }
    }
  }

}
