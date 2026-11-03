package com.togger.milestone

import com.togger.auth.SecuredEndpoint
import com.togger.common.PageRequest
import com.togger.common.PaginatedResponse
import com.togger.common.PaginationInputs.pageQuery
import com.togger.common.TgEndpoints.TgSecuredEndpoint
import com.togger.common.TgEndpoints.TgSecuredEndpointEmptyBody
import com.togger.common.TgJsonSuccess
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.dto.CreateMilestoneStepReq
import com.togger.dto.FindMilestoneStepResp
import com.togger.dto.MilestoneStepVersionDto
import com.togger.dto.ReorderMilestoneStepsReq
import com.togger.dto.UpdateMilestoneStepReq
import io.circe.generic.auto._
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

import java.util.UUID

class MilestoneStepEndpoints(securedEndpoint: SecuredEndpoint) {

  private val basePath: EndpointInput[Unit] = "milestone-step"

  val saveEndpoint: TgSecuredEndpoint[CreateMilestoneStepReq, FindMilestoneStepResp] =
    securedEndpoint.endpoint.post
      .in(basePath)
      .in(jsonBody[CreateMilestoneStepReq])
      .out(jsonBody[TgJsonSuccess[FindMilestoneStepResp]])

  val findByIdEndpoint: TgSecuredEndpoint[UUID, FindMilestoneStepResp] =
    securedEndpoint.endpoint.get
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[FindMilestoneStepResp]])

  /** Saves a new version; the change comment is mandatory. */
  val updateEndpoint: TgSecuredEndpoint[UpdateMilestoneStepReq, FindMilestoneStepResp] =
    securedEndpoint.endpoint.put
      .in(basePath)
      .in(jsonBody[UpdateMilestoneStepReq])
      .out(jsonBody[TgJsonSuccess[FindMilestoneStepResp]])

  /** All steps of the milestone ordered by position (stage); a milestone has a short list of them, so it is not paged.
    */
  val findByMilestoneIdEndpoint: TgSecuredEndpoint[UUID, List[FindMilestoneStepResp]] =
    securedEndpoint.endpoint.get
      .in(basePath / "milestone" / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[List[FindMilestoneStepResp]]])

  /** All steps of the milestones of the project, by milestone then position, for the pickers of the task forms. */
  val findByProjectIdEndpoint: TgSecuredEndpoint[UUID, List[FindMilestoneStepResp]] =
    securedEndpoint.endpoint.get
      .in(basePath / "project" / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[List[FindMilestoneStepResp]]])

  val reorderEndpoint: TgSecuredEndpoint[ReorderMilestoneStepsReq, List[FindMilestoneStepResp]] =
    securedEndpoint.endpoint.put
      .in(basePath / "order")
      .in(jsonBody[ReorderMilestoneStepsReq])
      .out(jsonBody[TgJsonSuccess[List[FindMilestoneStepResp]]])

  val deleteByIdEndpoint: TgSecuredEndpointEmptyBody[UUID] =
    securedEndpoint.endpoint.delete
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[TgEmptyBody]])

  val listVersionsEndpoint: TgSecuredEndpoint[(UUID, PageRequest), PaginatedResponse[MilestoneStepVersionDto]] =
    securedEndpoint.endpoint.get
      .in(basePath / path[UUID]("id") / "version")
      .in(pageQuery)
      .out(jsonBody[TgJsonSuccess[PaginatedResponse[MilestoneStepVersionDto]]])

}
