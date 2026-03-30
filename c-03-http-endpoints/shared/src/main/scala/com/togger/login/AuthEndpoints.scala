package com.togger.login

import com.togger.auth.SecuredEndpoint
import com.togger.common.TgEndpoints.{TgBaseEndpoint, TgPublicEndpoint, TgSecuredEndpointEmptyBody}
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.{TgJsonFailure, TgJsonSuccess}
import com.togger.dto.login._
import io.circe.generic.auto._
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

class AuthEndpoints(baseEndpoint: TgBaseEndpoint, securedEndpoint: SecuredEndpoint) {

  private val basePath: EndpointInput[Unit] = "account"

  def loginEndpoint: PublicEndpoint[LoginDto, TgJsonFailure, TgJsonSuccess[LoginResponseDto], Any] =
    baseEndpoint.post
      .in(basePath / "login")
      .in(jsonBody[LoginDto])
      .out(jsonBody[TgJsonSuccess[LoginResponseDto]])

  def expireSessions: PublicEndpoint[Unit, TgJsonFailure, TgJsonSuccess[ExpireSessionResult], Any] = {
    baseEndpoint.get
      .in(basePath / "expire-sessions")
      .out(jsonBody[TgJsonSuccess[ExpireSessionResult]])
  }
  //      .tag(topic)
  //      .name(createName(findById))
  //      .summary(createSummary(findById))
  //      .description(s"Find $topic using id")

  def logoutEveryone: TgPublicEndpoint[String, LogoutEveryoneDto] =
    baseEndpoint.get
      .in(basePath / "log-out-everyone" / path[String]("password"))
      .out(jsonBody[TgJsonSuccess[LogoutEveryoneDto]])

//  val exampleAuth: Full[String, String, String, TgJsonError, TgJsonSuccess[String], Any, IO ] =
  val exampleAuth =
    endpoint.get
      .in(basePath / "sec-test")
      .out(jsonBody[TgJsonSuccess[String]])
      .errorOut(jsonBody[TgJsonFailure])
      .securityIn(auth.bearer[String]())

  val changePassword: TgSecuredEndpointEmptyBody[ChangePasswordDto] =
    securedEndpoint.endpoint.post
      .in(basePath)
      .in(jsonBody[ChangePasswordDto])
      .out(jsonBody[TgJsonSuccess[TgEmptyBody]])
//      tag = topic,
//      topic = "Change password for business account",
//      description = "Change password"

}
