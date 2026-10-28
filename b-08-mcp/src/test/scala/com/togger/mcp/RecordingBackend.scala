package com.togger.mcp

import scala.collection.mutable.ListBuffer

import sttp.client4.*
import sttp.client4.testing.*
import sttp.model.StatusCode

case class SeenRequest(method: String, uri: String, authorization: Option[String], body: Option[String])

/** A backend stand-in: records every request and answers with whatever `respond` returns for it. */
class RecordingBackend(respond: SeenRequest => (StatusCode, String)):
  val seen: ListBuffer[SeenRequest] = ListBuffer.empty

  val backend: SyncBackend = SyncBackendStub.whenAnyRequest.thenRespondF { request =>
    val body = request.body match
      case StringBody(text, _, _) => Some(text)
      case _                      => None
    val call = SeenRequest(request.method.method, request.uri.toString, request.header("Authorization"), body)
    seen += call
    val (code, text) = respond(call)
    ResponseStub.adjust(text, code)
  }

object RecordingBackend:
  def envelope(body: String): String = s"""{"body":$body}"""
  val LoginOk: String = envelope("""{"accountId":"11111111-1111-1111-1111-111111111111","sessionId":"token-1"}""")
