package com.togger.health

import com.togger.dto.HealthResp
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class HealthControllerTest extends AnyWordSpec with Matchers {

  "HealthController" should {
    "answer the app name and the version" in {
      new HealthController("1.2.3").response shouldBe HealthResp("backend", "1.2.3")
    }

    "serve a public GET /health endpoint" in {
      val endpoint = new HealthController("1.2.3").health.endpoint
      endpoint.method.map(_.method) shouldBe Some("GET")
      endpoint.showPathTemplate() should include("/health")
      endpoint.securityInput.show should not include "Authorization"
    }
  }
}
