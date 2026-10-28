package com.togger.mcp

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import sttp.model.StatusCode

class TgBackendClientTest extends AnyWordSpec with Matchers:
  private val config =
    McpConfig("http://backend:8081", "127.0.0.1", 8090, McpConfig.AccountEmail, McpConfig.AccountPassword, None)

  "TgBackendClient" should {

    "log in with the mcp credentials and send the session as bearer token on later calls" in {
      val stub = RecordingBackend {
        case SeenRequest("POST", uri, _, _) if uri.endsWith("/account/login") =>
          (StatusCode.Ok, RecordingBackend.LoginOk)
        case _ => (StatusCode.Ok, RecordingBackend.envelope("""{"name":"org"}"""))
      }
      val client = TgBackendClient(config, stub.backend)

      client.login() shouldBe Right(())
      val result = client.get(List("organization", "abc"), List("page" -> "2"))

      result.map(_.noSpaces) shouldBe Right("""{"name":"org"}""")
      stub.seen.head.body.get should include(McpConfig.AccountEmail)
      stub.seen.head.authorization shouldBe None
      stub.seen(1).uri shouldBe "http://backend:8081/organization/abc?page=2"
      stub.seen(1).authorization shouldBe Some("Bearer token-1")
    }

    "report a failed login" in {
      val stub = RecordingBackend(_ => (StatusCode.BadRequest, """{"errors":[{"message":"Invalid credentials"}]}"""))
      TgBackendClient(config, stub.backend).login().left.map(_.contains("400")) shouldBe Left(true)
    }

    "log in again once and retry when the backend answers 401" in {
      var logins = 0
      val stub = RecordingBackend {
        case SeenRequest("POST", _, _, _) =>
          logins += 1
          (StatusCode.Ok, RecordingBackend.envelope(s"""{"sessionId":"token-$logins"}"""))
        case SeenRequest(_, _, Some("Bearer token-1"), _) => (StatusCode.Unauthorized, "")
        case _                                            => (StatusCode.Ok, RecordingBackend.envelope("[]"))
      }
      val client = TgBackendClient(config, stub.backend)
      val _ = client.login()

      client.get(List("organization")).map(_.noSpaces) shouldBe Right("[]")
      logins shouldBe 2
      stub.seen.last.authorization shouldBe Some("Bearer token-2")
    }

    "describe backend failures" in {
      val stub = RecordingBackend {
        case SeenRequest("POST", _, _, _)                           => (StatusCode.Ok, RecordingBackend.LoginOk)
        case SeenRequest(_, uri, _, _) if uri.contains("missing")   => (StatusCode.NotFound, "")
        case SeenRequest(_, uri, _, _) if uri.contains("forbidden") => (StatusCode.Forbidden, "")
        case _ => (StatusCode.InternalServerError, """{"msg":"boom"}""")
      }
      val client = TgBackendClient(config, stub.backend)
      val _ = client.login()

      client.get(List("missing")) shouldBe Left("Not found (404)")
      client.get(List("forbidden")) shouldBe Left("Backend refused access (403)")
      client.get(List("other")) shouldBe Left("""Backend error 500: {"msg":"boom"}""")
    }

    "fail when it was never logged in" in {
      val stub = RecordingBackend(_ => (StatusCode.Ok, RecordingBackend.LoginOk))
      TgBackendClient(config, stub.backend).get(List("organization")) shouldBe Left(
        "Not logged in to the Togger backend"
      )
      stub.seen shouldBe empty
    }
  }
