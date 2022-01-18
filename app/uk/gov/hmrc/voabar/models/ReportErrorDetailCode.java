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

package uk.gov.hmrc.voabar.models;

public enum ReportErrorDetailCode {
    Cr01AndCr02MissingExistingEntryValidation,
    Cr03AndCr04MissingProposedEntryValidation,
    Cr05AndCr12MissingProposedEntryValidation,
    Cr06AndCr07AndCr09AndCr10AndCr14MissingProposedEntryValidation,
    Cr08InvalidCodeValidation,
    Cr11InvalidCodeValidation,
    Cr13InvalidCodeValidation,
    TextAddressPostcodeValidation,
    OccupierContactAddressesPostcodeValidation,
    /**
     * It's automatically corrected. Should never happen.
     */
    RemarksValidationNotEmpty,
    RemarksValidationTooLong,
    PropertyPlanReferenceNumberValidation,

    Rt01AndRt04AndRt03AndRt04MissingProposedEntryValidation,
    Rt05AndRt06AndRt07AndRt08AndRt9AndRt11MissingExistingEntryValidation,
    InvalidNdrCode,
    NoNDRCode
}
