package com.togger.controllers.milestone

import cats.effect.IO
import com.togger.ai.{TgAiChunk, TgAiModel}
import com.togger.common.TgError._
import com.togger.common.TgResponseTypes.TgAuth
import com.togger.common.{DateRangeValidation, Forbidden, TgJsonFailure}
import com.togger.contribution.ContributionService
import com.togger.dto.SummariseMilestoneWithAiReq
import com.togger.logging.TgLogging
import com.togger.milestone.MilestoneAiSummaryStreamContract._
import com.togger.milestone.{MilestoneAiSummaryCommand, MilestoneAiSummaryService, MilestoneService}
import io.circe.Json
import sttp.model.sse.ServerSentEvent
import sttp.tapir.server.http4s.Http4sServerSentEvents

class MilestoneAiSummaryController(
  milestoneAiSummaryEndpoints: MilestoneAiSummaryStreamEndpoints,
  milestoneAiSummaryService: MilestoneAiSummaryService,
  milestoneService: MilestoneService,
  dateRangeValidation: DateRangeValidation,
  contributionService: ContributionService
) extends TgLogging {
  private type Events = fs2.Stream[IO, Byte]

  val summarise = milestoneAiSummaryEndpoints.summariseStreamEndpoint.serverLogic { auth => dto =>
    withProjectMember(dto.milestoneId, auth) {
      dateRangeValidation.validateOnOrAfterStart("End date", dto.endDate, dto.startDate).toEither match {
        case Left(errors) => IO.pure(Left(errors.toBadRequest))
        case Right(_) =>
          milestoneAiSummaryService.summariseStream(auth.body.get.id, toCommand(dto)).map {
            case Right(chunks) => Right(toEvents(chunks).through(Http4sServerSentEvents.serialiseSSEToBytes[IO]))
            case Left(error)   => Left(error.toBadRequest)
          }
      }
    }
  }

  private def toEvents(chunks: fs2.Stream[IO, TgAiChunk]): fs2.Stream[IO, ServerSentEvent] =
    (chunks.map(chunk => ServerSentEvent(Some(Json.fromString(chunk.delta).noSpaces), Some(ChunkEvent))) ++
      fs2.Stream.emit(ServerSentEvent(Some(""), Some(DoneEvent)))).handleErrorWith { error =>
      fs2.Stream.eval(IO(logger.error(s"The AI summary stream failed: $error"))) >>
        fs2.Stream.emit(ServerSentEvent(Some("The AI stopped answering"), Some(ErrorEvent)))
    }

  private def toCommand(dto: SummariseMilestoneWithAiReq): MilestoneAiSummaryCommand =
    MilestoneAiSummaryCommand(
      milestoneId = dto.milestoneId,
      promptId = dto.promptId,
      summarizePrompt = dto.summarizePrompt,
      startDate = dto.startDate,
      endDate = dto.endDate,
      model = TgAiModel(dto.model)
    )

  private def withProjectMember(milestoneId: java.util.UUID, auth: TgAuth)(
    f: => IO[Either[TgJsonFailure, Events]]
  ): IO[Either[TgJsonFailure, Events]] =
    milestoneService.findProjectId(milestoneId).flatMap { projectId =>
      contributionService.isMember(projectId, auth.body.get.id).flatMap {
        case false => IO.pure(Left(Forbidden))
        case true  => f
      }
    }
}
