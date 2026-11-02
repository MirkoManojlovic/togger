package com.togger.controllers.milestone

import cats.effect.IO
import com.togger.auth.SecuredEndpoint
import com.togger.common.TgJsonFailure
import com.togger.common.TgResponseTypes
import com.togger.dto.SummariseMilestoneWithAiReq
import com.togger.milestone.MilestoneAiSummaryStreamContract
import io.circe.generic.auto._
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.PartialServerEndpoint

import java.nio.charset.StandardCharsets

class MilestoneAiSummaryStreamEndpoints(securedEndpoint: SecuredEndpoint) {

  val summariseStreamEndpoint: PartialServerEndpoint[
    String,
    TgResponseTypes.TgAuth,
    SummariseMilestoneWithAiReq,
    TgJsonFailure,
    fs2.Stream[IO, Byte],
    Fs2Streams[IO],
    IO
  ] =
    securedEndpoint.endpoint.post
      .in(MilestoneAiSummaryStreamContract.PathSegments.foldLeft(emptyInput)(_ / _))
      .in(jsonBody[SummariseMilestoneWithAiReq])
      .out(streamTextBody(Fs2Streams[IO])(CodecFormat.TextEventStream(), Some(StandardCharsets.UTF_8)))
}
