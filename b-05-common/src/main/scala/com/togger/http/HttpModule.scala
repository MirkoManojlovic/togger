package com.togger.http

import cats.data.Kleisli
import cats.effect.{IO, Resource}
import com.comcast.ip4s.IpLiteralSyntax
import com.togger.logging.TgLogging
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.headers.Origin
import org.http4s.server.Router
import org.http4s.server.middleware.{CORS, CORSPolicy}
import org.http4s.{Request, Response, Uri, server}
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.http4s.{Http4sServerInterpreter, Http4sServerOptions}

import scala.concurrent.duration._
import scala.util.chaining.scalaUtilChainingOps
class HttpModule extends TgLogging {

  val corsConf: CORSPolicy =
    CORS.policy
      .withAllowOriginHost(
        Set(
          // TODO: move this to dev config
          Origin.Host(Uri.Scheme.http, Uri.RegName("localhost"), Some(5173)),
          Origin.Host(Uri.Scheme.fromString("tauri").toOption.get, Uri.RegName("localhost"))
          //          Origin.Host(Uri.Scheme.http, Uri.RegName("localhost"), Some(63342))
        )
      )
      .withAllowCredentials(false)
      .withMaxAge(1.day)

  val defaultServerLog = Http4sServerOptions.defaultServerLog[IO]
  val newLogSettings = defaultServerLog.copy(
    logWhenHandled = true,
    logAllDecodeFailures = true,
    logLogicExceptions = true,
    doLogExceptions = (m, t) => IO(logger.error(s"A message $m and and error $t"))
  )

  lazy val interpreter: Http4sServerInterpreter[IO] =
    Http4sServerOptions
      .customiseInterceptors[IO]
      .serverLog(newLogSettings)
      .options
      .pipe(Http4sServerInterpreter[IO])

  def createServer(endpoints: List[ServerEndpoint[Fs2Streams[IO], IO]]): Resource[IO, server.Server] =
    EmberServerBuilder
      .default[IO]
      .withHost(ipv4"0.0.0.0") // TODO: move this to config
      .withPort(port"8081")
      .withHttpApp(createHttpApp(endpoints))
      .build

  def createHttpApp(endpoints: List[ServerEndpoint[Fs2Streams[IO], IO]]): Kleisli[IO, Request[IO], Response[IO]] =
    Router(
      "/" -> corsConf.apply(interpreter.toRoutes(endpoints))
      //        "swagger" -> corsConf.apply(appDocs.swaggerRoutes),
      //        "redoc" -> corsConf.apply(appDocs.redocRoutes)
    ).orNotFound

}
