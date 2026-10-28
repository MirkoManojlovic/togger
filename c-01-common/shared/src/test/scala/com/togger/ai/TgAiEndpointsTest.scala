package com.togger.ai

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class TgAiEndpointsTest extends AnyWordSpec with Matchers {

  "TgAiEndpoints" should {

    "have unique values" in {
      TgAiEndpoints.values.map(_.value).distinct.size shouldBe TgAiEndpoints.values.size
    }

    "have unique endpoint names" in {
      TgAiEndpoints.values.map(_.endpointName).distinct.size shouldBe TgAiEndpoints.values.size
    }

    "resolve the milestone summary by its value" in {
      TgAiEndpoints.withValue(0) shouldBe TgAiEndpoints.MilestoneSummary
    }
  }
}
