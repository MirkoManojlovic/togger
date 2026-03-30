package com.togger.common

import cats.effect.IO
import com.togger.auth.AuthAccount
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.TgResponseTypes.TgAuth
import sttp.tapir.server.PartialServerEndpoint
import sttp.tapir.server.ServerEndpoint.Full
import sttp.tapir.{Endpoint, PublicEndpoint}

object TgEndpoints {

  type TgBaseEndpoint =
    Endpoint[Unit, Unit, TgJsonFailure, Unit, Any]

  type TgPublicEndpoint[INPUT, OUTPUT] =
    PublicEndpoint[INPUT, TgJsonFailure, TgJsonSuccess[OUTPUT], Any]

  type TgSecuredEndpoint[INPUT, OUTPUT] =
    PartialServerEndpoint[String, TgAuth, INPUT, TgJsonFailure, TgJsonSuccess[OUTPUT], Any, IO]

  type TgSecuredEndpointNoInput[OUTPUT] =
    TgSecuredEndpoint[Unit, OUTPUT]

  type TgSecuredEndpointEmptyBody[INPUT] =
    PartialServerEndpoint[String, TgAuth, INPUT, TgJsonFailure, TgJsonSuccess[TgEmptyBody], Any, IO]

  type TgSecuredEndpointFull[INPUT, OUTPUT] =
    Full[String, TgAuth, INPUT, TgJsonFailure, TgJsonSuccess[OUTPUT], Any, IO]

}

object TgResponseTypes {
  type TgResp[T] = Either[TgJsonFailure, TgJsonSuccess[T]]
  type TgRespIO[T] = IO[TgResp[T]]

  type TgRespEmptyBody = Either[TgJsonFailure, TgJsonSuccess[TgEmptyBody]]
  type TgRespEmptyBodyIO = IO[Either[TgJsonFailure, TgJsonSuccess[TgEmptyBody]]]

  type TgAuth = TgJsonSuccess[AuthAccount]

}
