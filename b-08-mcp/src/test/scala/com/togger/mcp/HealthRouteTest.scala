package com.togger.mcp

import io.circe.parser.parse
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class HealthRouteTest extends AnyWordSpec with Matchers:

  "HealthRoute" should {
    "answer the app name and the version in the body" in {
      val json = parse(HealthRoute("1.2.3").body).toOption.get.hcursor.downField("body")
      json.get[String]("app") shouldBe Right("mcp")
      json.get[String]("version") shouldBe Right("1.2.3")
    }

    "be a GET /health endpoint without security input" in {
      val endpoint = HealthRoute("1.2.3").endpoint.endpoint
      endpoint.method.map(_.method) shouldBe Some("GET")
      endpoint.showPathTemplate() should include("/health")
      endpoint.securityInput.show should not include "Authorization"
    }
  }
