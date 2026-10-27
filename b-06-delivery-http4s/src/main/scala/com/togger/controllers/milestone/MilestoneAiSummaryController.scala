package com.togger.controllers.milestone

import cats.effect.IO
import cats.implicits._
import com.togger.ai.TgAiModel
import com.togger.common.TgError._
import com.togger.common.TgResponseTypes.TgAuth
import com.togger.common.{DateRangeValidation, Forbidden, TgJsonFailure, TgJsonSuccess}
import com.togger.contribution.ContributionService
import com.togger.dto.{SummariseMilestoneWithAiReq, SummariseMilestoneWithAiResp}
import com.togger.milestone.{
  MilestoneAiSummaryCommand,
  MilestoneAiSummaryEndpoints,
  MilestoneAiSummaryService,
  MilestoneService
}

class MilestoneAiSummaryController(
  milestoneAiSummaryEndpoints: MilestoneAiSummaryEndpoints,
  milestoneAiSummaryService: MilestoneAiSummaryService,
  milestoneService: MilestoneService,
  dateRangeValidation: DateRangeValidation,
  contributionService: ContributionService
) {

  val summarise = milestoneAiSummaryEndpoints.summariseEndpoint.serverLogic { auth => dto =>
    withProjectMember(dto.milestoneId, auth) {
      dateRangeValidation.validateOnOrAfterStart("End date", dto.endDate, dto.startDate).toEither match {
        case Left(errors) => IO.pure(errors.toBadRequestLeft[SummariseMilestoneWithAiResp])
        case Right(_) =>
          milestoneAiSummaryService.summarise(auth.body.get.id, toCommand(dto)).map {
            case Right(summary) => TgJsonSuccess.justBodyRight(SummariseMilestoneWithAiResp(summary))
            case Left(error)    => error.toBadRequestLeft[SummariseMilestoneWithAiResp]
          }
      }
    }
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
    f: => IO[Either[TgJsonFailure, TgJsonSuccess[SummariseMilestoneWithAiResp]]]
  ): IO[Either[TgJsonFailure, TgJsonSuccess[SummariseMilestoneWithAiResp]]] =
    milestoneService.findProjectId(milestoneId).flatMap { projectId =>
      contributionService.isMember(projectId, auth.body.get.id).flatMap {
        case false => IO.pure(Left(Forbidden))
        case true  => f
      }
    }
}
