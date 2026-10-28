package com.togger.mcp

import java.util.concurrent.atomic.AtomicReference

import io.circe.Json
import io.circe.parser.parse
import sttp.client4.*
import sttp.model.{StatusCode, Uri}

/** Calls the Togger backend as the mcp account. The session token never expires on the backend, so it is fetched once
  * at startup; a 401 (for example after the sessions were wiped) triggers one new login and one retry.
  */
class TgBackendClient(config: McpConfig, backend: SyncBackend):
  import TgBackendClient.Failure

  private val token = new AtomicReference[Option[String]](None)

  def login(): Either[String, Unit] =
    val loginBody = Json.obj(
      "email" -> Json.fromString(config.email),
      "password" -> Json.fromString(config.password),
      "deviceInfo" -> Json.obj("browser" -> Json.fromString("togger-mcp"), "ipAddress" -> Json.fromString("127.0.0.1"))
    )
    val request =
      basicRequest.post(uri(List("account", "login"), Nil)).contentType("application/json").body(loginBody.noSpaces)
    val sessionId = for
      body <- execute(request)
      id <- body.hcursor.get[String]("sessionId").left.map(_ => Failure.Message("Login response has no sessionId"))
    yield id
    sessionId.left.map(_.describe).map(id => token.set(Some(id)))

  def get(path: List[String], query: List[(String, String)] = Nil): Either[String, Json] =
    authorized(basicRequest.get(uri(path, query)))

  def post(path: List[String], query: List[(String, String)], body: Json): Either[String, Json] =
    authorized(basicRequest.post(uri(path, query)).contentType("application/json").body(body.noSpaces))

  private def authorized(request: Request[Either[String, String]]): Either[String, Json] =
    withToken(request) match
      case Left(Failure.Http(StatusCode.Unauthorized, _)) =>
        login().flatMap(_ => withToken(request).left.map(_.describe))
      case other => other.left.map(_.describe)

  private def withToken(request: Request[Either[String, String]]): Either[Failure, Json] =
    token.get() match
      case None               => Left(Failure.Message("Not logged in to the Togger backend"))
      case Some(sessionToken) => execute(request.auth.bearer(sessionToken))

  private def execute(request: Request[Either[String, String]]): Either[Failure, Json] =
    for
      response <- send(request)
      text <- response.body.left.map(error => Failure.Http(response.code, error))
      json <- parse(text).left.map(e => Failure.Message(s"Backend returned invalid JSON: ${e.message}"))
    yield json.hcursor.downField("body").focus.getOrElse(Json.Null)

  private def send(request: Request[Either[String, String]]): Either[Failure, Response[Either[String, String]]] =
    try Right(request.send(backend))
    catch
      case e: Exception =>
        Left(Failure.Message(s"Cannot reach the Togger backend at ${config.backendUrl}: ${e.getMessage}"))

  private def uri(path: List[String], query: List[(String, String)]): Uri =
    query.foldLeft(Uri.unsafeParse(config.backendUrl).addPath(path))((acc, param) => acc.addParam(param._1, param._2))

object TgBackendClient:
  private enum Failure:
    case Message(text: String)
    case Http(code: StatusCode, body: String)

    def describe: String = this match
      case Message(text)                    => text
      case Http(StatusCode.Unauthorized, _) => "Backend rejected the mcp session (401)"
      case Http(StatusCode.Forbidden, _)    => "Backend refused access (403)"
      case Http(StatusCode.NotFound, _)     => "Not found (404)"
      case Http(code, text)                 => s"Backend error ${code.code}: ${text.take(500)}"
