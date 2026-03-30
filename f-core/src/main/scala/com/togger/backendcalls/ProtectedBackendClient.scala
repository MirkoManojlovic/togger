package com.togger.backendcalls

import cats.effect.IO
import com.togger.common.TgJsonFailure
import com.togger.common.TgEndpoints.TgSecuredEndpoint
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.logging.TgLogging
import com.togger.ui.auth.LoginRepo
import sttp.capabilities
import sttp.client3.SttpBackend
import sttp.model.Uri
import sttp.tapir.client.sttp.SttpClientInterpreter

class ProtectedBackendClient(
  loginRepo: LoginRepo,
  baseUri: Uri = UnprotectedBackendClient.baseUri,
  interpreter: SttpClientInterpreter = UnprotectedBackendClient.interpreter,
  backend: SttpBackend[IO, capabilities.WebSockets] = UnprotectedBackendClient.catsBackend
) extends TgLogging {

  def protectedHttpCall[INPUT, OUTPUT](
    secureEndpoint: TgSecuredEndpoint[INPUT, OUTPUT]
  ): INPUT => TgRespIO[OUTPUT] = { input =>
    loginRepo.account
      .map { authAccount =>
        logger.debug(s"Calling endpoint with initial access token")
        val httpCall = callEndpointWithAccessToken(input, secureEndpoint)
        httpCall(authAccount.sessionId).flatMap { response =>
          logger.debug(s"Response is ${(response)}")
          response.fold(
            {
//              case Unauthenticated =>
//                logger.debug(s"Trying to refresh token and attempt again")
//                tokenPairFacade.refresh
//                  .flatMap { tokenPairResp =>
//                    logger.debug(s"Refresh token operation response ${sp(tokenPairResp)}")
//                    tokenPairResp.fold(failToRefreshToken, retryCall(httpCall))
//                  }
              case failure: TgJsonFailure => {
                logger.error(s"Failed to make the call. ${(failure)}")
                IO(Left(failure))
              }
            },
            success => IO(Right(success))
          )
        }

      }
      .getOrElse {
        logger.error(s"Initial access token not found, logging out.")
        loginRepo.delete()
        TgJsonFailure.unauthenticatedLeftIO(None)
      }
  }

  private def callEndpointWithAccessToken[INPUT, OUTPUT](
    input: INPUT,
    secureEndpoint: TgSecuredEndpoint[INPUT, OUTPUT]
  ): String => TgRespIO[OUTPUT] =
    configuredInterpreter(secureEndpoint).apply(_: String)(input)

  private def configuredInterpreter[INPUT, OUTPUT](
    secureEndpoint: TgSecuredEndpoint[INPUT, OUTPUT]
  ): String => INPUT => TgRespIO[OUTPUT] =
    interpreter
      .toSecureClientThrowDecodeFailures(
        e = secureEndpoint.endpoint,
        baseUri = Some(baseUri),
        backend = backend
      )

}
