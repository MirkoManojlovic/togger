package com.togger.backendcalls

import cats.effect.IO
import com.togger.common.{TgJsonFailure, TgJsonSuccess}
import scribe.Logging
import sttp.capabilities
import sttp.client3._
import sttp.client3.impl.cats.FetchCatsBackend
import sttp.model.Uri
import sttp.tapir.PublicEndpoint
import sttp.tapir.client.sttp.SttpClientInterpreter

class UnprotectedBackendClient(
  baseUri: Uri = UnprotectedBackendClient.baseUri,
  interpreter: SttpClientInterpreter = UnprotectedBackendClient.interpreter,
  backend: SttpBackend[IO, capabilities.WebSockets] = UnprotectedBackendClient.catsBackend
) extends Logging {

  def httpCall[INPUT, OUTPUT](
    endpoint: PublicEndpoint[INPUT, TgJsonFailure, TgJsonSuccess[OUTPUT], Any]
  ): INPUT => IO[Either[TgJsonFailure, TgJsonSuccess[OUTPUT]]] =
    input =>
      configuredInterpreter(endpoint)
        .apply(input)
        .handleError { error =>
          logger.error(s"An ${(error)} when calling ${(endpoint.info)}")
          TgJsonFailure.internalErrorLeft(Some("Oops something went wrong"))
        }

  private def configuredInterpreter[INPUT, OUTPUT](
    endpoint: PublicEndpoint[INPUT, TgJsonFailure, TgJsonSuccess[OUTPUT], Any]
  ): INPUT => IO[Either[TgJsonFailure, TgJsonSuccess[OUTPUT]]] =
    interpreter
      .toClientThrowDecodeFailures[IO, INPUT, TgJsonFailure, TgJsonSuccess[OUTPUT], Any](
        endpoint,
        Some(baseUri),
        backend
      )
}

object UnprotectedBackendClient {
  val interpreter: SttpClientInterpreter = SttpClientInterpreter()
  // TODO 0: make this configurable from app config
  val baseUri: Uri = uri"http://localhost:8081/"
  val catsBackend: SttpBackend[IO, capabilities.WebSockets] = FetchCatsBackend[IO]()
}
