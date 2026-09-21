/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.preferencesadminfrontend.config

import org.scalatestplus.play.PlaySpec

class FormIdsSpec extends PlaySpec {

  "configList" should {
    val formIdsConfig: Seq[String] = FormIds.configList

    "contain ITSA form ids" in {
      val itsaFormIds: List[String] =
        List(
          "LPP1A_ITSA",
          "LPP1B_ITSA",
          "LPP2_ITSA",
          "LPP4_ITSA",
          "LPP5_ITSA",
          "LPP6_ITSA",
          "PAR1_ITSA",
          "ITSAORM1",
          "PL3"
        )

      itsaFormIds.foreach { itsaFormId =>
        assert(formIdsConfig.contains(itsaFormId))
      }
    }

    "contain NIRef form ids" in {
      val niRefFormIds: List[String] =
        List(
          "NIRef1",
          "NIRef2",
          "NIRef3",
          "NIRef4"
        )

      niRefFormIds.foreach { id =>
        assert(formIdsConfig.contains(id))
      }
    }

    "contain Low Earners Pension Payment (LEPP) form ids" in {
      val lEPPFormIds: List[String] = List("LEPP1", "LEPP2", "LEPP3", "LEPP4")

      lEPPFormIds.foreach { id =>
        assert(formIdsConfig.contains(id))
      }
    }

    "contain CH(A)1700 and CH(A)1708 form ids" in {
      val CHAFormIds: List[String] = List("CH(A)1700", "CH(A)1708")

      CHAFormIds.foreach { id =>
        assert(formIdsConfig.contains(id))
      }
    }

    "contain VPD formId" in {
      assert(formIdsConfig.contains("VPD1"))
    }

    "not contain welsh form ids" in {
      assert(!FormIds.configList.exists(_.toLowerCase().endsWith("_cy")))
    }
  }
}
