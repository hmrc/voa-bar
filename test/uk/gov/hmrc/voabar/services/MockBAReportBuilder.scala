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

import scala.xml._
import scala.xml.transform.{RewriteRule, RuleTransformer}

class MockBAReportBuilder {

  private val baReportCodes: Map[String, String] = Map(
    "CR03" -> "New",
    "CR04" -> "Change to domestic use",
    "CR05" -> "Reconstituted Property",
    "CR08" -> "NOT IN USE",
    "CR11" -> "Boundary Change - NOT IN USE",
    "CR12" -> "Major Address Change - NOT IN USE",
    "CR13" -> "Boundary Change, Add DO NOT USE THIS CODE",
    "CR15" -> "Enquiry Received - Add DO NOT USE THIS CODE",
    "CR16" -> "Consequential to band alteration for a neighbouring dwelling - Add DO NOT USE THIS CODE",
    "CR99" -> "NOT A VOA CODE - USED FOR TEST PURPOSES"
  )

  def apply(reasonCode: String, baCode: Int, existingEntries: Int, proposedEntries: Int): NodeSeq = {

    val node =
      <BApropertyReport>
        <DateSent>2017-03-18</DateSent>
        <TransactionIdentityBA>16286449061000</TransactionIdentityBA>
        <BAidentityNumber>{baCode}</BAidentityNumber>
        <BAreportNumber>118294</BAreportNumber>
        <TypeOfTax>
          <CtaxReasonForReport>
            <ReasonForReportCode>{reasonCode}</ReasonForReportCode>
            <ReasonForReportDescription>{baReportCodes(reasonCode)}</ReasonForReportDescription>
          </CtaxReasonForReport>
        </TypeOfTax>
        <IndicatedDateOfChange>2017-03-17</IndicatedDateOfChange>
        <Remarks>Some remarks that help to describe this property report submission</Remarks>
      </BApropertyReport>

    val newNode = concat(NodeSeq.Empty, existingEntries, proposedEntries)

    val newChilds: NodeSeq = node.child.foldLeft(NodeSeq.Empty)((acc, elem) =>
      if (elem.label == "TypeOfTax") acc ++ elem ++ newNode else acc ++ elem
    )

    val root = <BApropertyReport></BApropertyReport>

    def addNode(root: Node, children: NodeSeq) = (root: @unchecked) match {
      case Elem(prefix, label, attributes, scope, child @ _*) =>
        Elem(prefix, label, attributes, scope, false, child ++ children: _*)
    }
    addNode(root, newChilds)
  }

  private def concat(node: NodeSeq, existing: Int, proposed: Int): NodeSeq = existing match {
    case 0 => if (proposed == 0) {
        node
      } else {
        concat(node ++ proposedEntries, 0, proposed - 1)
      }
    case _ => concat(node ++ existingEntries, existing - 1, proposed)
  }

  private def invalidate(existingVal: String, newValue: String) = new RewriteRule {

    override def transform(node: Node): Node = node match {
      case e: Elem if e.label == existingVal => e.copy(label = newValue)
      case e: Elem if e.text == existingVal  => e.copy(child = Text(newValue))
      case other                             => other
    }
  }

  private def invalidator(rule: RewriteRule, node: Node): Seq[Node] = {
    val transformer = new RuleTransformer(rule)
    transformer.transform(node)
  }

  private def invalidate(node: Node, key: String, newValue: String): Seq[Node] = invalidator(invalidate(key, newValue), node)

  def invalidateBatch(node: Node, rules: Map[String, String]): NodeSeq = {
    def inval(keys: List[String], n: NodeSeq): NodeSeq = keys match {
      case Nil      => n
      case hd :: tl => inval(tl, invalidate(n.head, hd, rules(hd)))
    }
    inval(rules.keySet.toList, node.theSeq)
  }

  private val existingEntries: NodeSeq =
    <ExistingEntries>
    <AssessmentProperties>
      <PropertyIdentity>
        <bs7666:BS7666Address>
          <bs7666:PAON>
            <bs7666:StartRange>
              <bs7666:Number>37</bs7666:Number>
            </bs7666:StartRange>
          </bs7666:PAON>
          <bs7666:StreetDescription>44 Old St</bs7666:StreetDescription>
          <bs7666:UniqueStreetReferenceNumber>240</bs7666:UniqueStreetReferenceNumber>
          <bs7666:Town>Wimbletown</bs7666:Town>
          <bs7666:PostTown>Wandletown</bs7666:PostTown>
          <bs7666:PostCode>WAN W765</bs7666:PostCode>
        </bs7666:BS7666Address>
        <TextAddress>
          <AddressLine>77 Middle St</AddressLine>
          <AddressLine>Wandlevillage</AddressLine>
          <AddressLine>Wandletown</AddressLine>
          <Postcode>WIN W887</Postcode>
        </TextAddress>
        <BAreference>00000970037003</BAreference>
      </PropertyIdentity>
      <PropertyDescription>
        <PropertyDescriptionText>Not Known</PropertyDescriptionText>
      </PropertyDescription>
      <CurrentTax>
        <CouncilTaxBand>F</CouncilTaxBand>
      </CurrentTax>
      <OccupierContact>
        <OccupierName>
          <pdt:PersonNameTitle>Mr</pdt:PersonNameTitle>
          <pdt:PersonGivenName>Sal</pdt:PersonGivenName>
          <pdt:PersonFamilyName>Wandle</pdt:PersonFamilyName>
          <pdt:PersonRequestedName>Mr Sel Wandle</pdt:PersonRequestedName>
        </OccupierName>
        <ContactAddress>
          <apd:Line>FAO Mr Pete Wobble</apd:Line>
          <apd:Line>77 Middle St</apd:Line>
          <apd:Line>Wimblevillage</apd:Line>
          <apd:Line>Wandletown</apd:Line>
          <apd:Line>Wandle County</apd:Line>
          <apd:PostCode>WIM W877</apd:PostCode>
        </ContactAddress>
      </OccupierContact>
    </AssessmentProperties>
  </ExistingEntries>

  private val proposedEntries: NodeSeq =
    <ProposedEntries>
      <AssessmentProperties>
        <PropertyIdentity>
          <bs7666:UniquePropertyReferenceNumber>10003070367</bs7666:UniquePropertyReferenceNumber>
          <bs7666:BS7666Address>
            <bs7666:PAON>
              <bs7666:StartRange>
                <bs7666:Number>3</bs7666:Number>
              </bs7666:StartRange>
            </bs7666:PAON>
            <bs7666:StreetDescription>23 New St</bs7666:StreetDescription>
            <bs7666:UniqueStreetReferenceNumber>5702038</bs7666:UniqueStreetReferenceNumber>
            <bs7666:Town>Windletown</bs7666:Town>
            <bs7666:AdministrativeArea>Wandle County</bs7666:AdministrativeArea>
            <bs7666:PostTown>Windletown</bs7666:PostTown>
            <bs7666:PostCode>WIM W877</bs7666:PostCode>
            <bs7666:UniquePropertyReferenceNumber>10003070367</bs7666:UniquePropertyReferenceNumber>
          </bs7666:BS7666Address>
          <PropertyGridCoords>
            <bs7666:X>000999.00</bs7666:X>
            <bs7666:Y>000999.00</bs7666:Y>
          </PropertyGridCoords>
          <TextAddress>
            <AddressLine>77 Middle St</AddressLine>
            <AddressLine>Windlevillage</AddressLine>
            <AddressLine>Windletown</AddressLine>
            <Postcode>WAN W765</Postcode>
          </TextAddress>
          <BAreference>11020978303000</BAreference>
        </PropertyIdentity>
        <CurrentTax>
          <CouncilTaxBand>F</CouncilTaxBand>
        </CurrentTax>
        <OccupierContact>
          <OccupierName>
            <pdt:PersonNameTitle>Mr</pdt:PersonNameTitle>
            <pdt:PersonGivenName>Sel</pdt:PersonGivenName>
            <pdt:PersonFamilyName>Wandle</pdt:PersonFamilyName>
            <pdt:PersonRequestedName>Mr Pete Wobble</pdt:PersonRequestedName>
          </OccupierName>
        </OccupierContact>
      </AssessmentProperties>
    </ProposedEntries>
}
