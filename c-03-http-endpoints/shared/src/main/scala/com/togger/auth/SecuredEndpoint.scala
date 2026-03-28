package com.togger.auth

import cats.effect.IO
import com.togger.common._
import com.togger.logging.TgLogging
import io.circe.generic.auto._
import sttp.model.StatusCode
import sttp.tapir.generic.auto.schemaForCaseClass
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.PartialServerEndpoint
import sttp.tapir.{Endpoint, auth, emptyOutputAs, endpoint, oneOf, oneOfVariant, statusCode}

class SecuredEndpoint(securityLogic: SecurityLogic) extends TgLogging {
  import BaseEndpoint._

  val endpoint: PartialServerEndpoint[String, TgJsonSuccess[AuthAccount], Unit, TgJsonFailure, Unit, Any, IO] =
    baseEndpoint
      .securityIn(auth.bearer[String]())
      .serverSecurityLogic(securityLogic.securityLogic)

}

object BaseEndpoint {
  val baseEndpoint: Endpoint[Unit, Unit, TgJsonFailure, Unit, Any] =
    endpoint.errorOut(
      oneOf[TgJsonFailure](
        oneOfVariant(statusCode(StatusCode.NotFound).and(emptyOutputAs(NotFound))),
        oneOfVariant(statusCode(StatusCode.BadRequest).and(jsonBody[BadRequest].description("Bad request"))),
        oneOfVariant(statusCode(StatusCode.InternalServerError).and(jsonBody[ServerError].description("Server error"))),
        oneOfVariant(
          statusCode(StatusCode.Unauthorized).and(emptyOutputAs(Unauthenticated).description("Unauthenticated"))
        ),
        oneOfVariant(statusCode(StatusCode.Forbidden).and(emptyOutputAs(Forbidden).description("Forbidden")))
      )
    )
}
