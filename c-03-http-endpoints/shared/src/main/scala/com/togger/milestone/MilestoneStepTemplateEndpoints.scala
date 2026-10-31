package com.togger.milestone

import com.togger.auth.SecuredEndpoint
import com.togger.common.PaginationInputs.pageQuery
import com.togger.common.TgEndpoints.{TgSecuredEndpoint, TgSecuredEndpointEmptyBody}
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.{PageRequest, PaginatedResponse, TgJsonSuccess}
import com.togger.dto.{
  CreateMilestoneStepTemplateReq,
  FindMilestoneStepTemplateResp,
  MilestoneStepTemplateVersionDto,
  UpdateMilestoneStepTemplateReq
}
import io.circe.generic.auto._
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

import java.util.UUID

class MilestoneStepTemplateEndpoints(securedEndpoint: SecuredEndpoint) {

  private val basePath: EndpointInput[Unit] = "milestone-step-template"

  val saveEndpoint: TgSecuredEndpoint[CreateMilestoneStepTemplateReq, FindMilestoneStepTemplateResp] =
    securedEndpoint.endpoint.post
      .in(basePath)
      .in(jsonBody[CreateMilestoneStepTemplateReq])
      .out(jsonBody[TgJsonSuccess[FindMilestoneStepTemplateResp]])

  val findByIdEndpoint: TgSecuredEndpoint[UUID, FindMilestoneStepTemplateResp] =
    securedEndpoint.endpoint.get
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[FindMilestoneStepTemplateResp]])

  val updateEndpoint: TgSecuredEndpoint[UpdateMilestoneStepTemplateReq, FindMilestoneStepTemplateResp] =
    securedEndpoint.endpoint.put
      .in(basePath)
      .in(jsonBody[UpdateMilestoneStepTemplateReq])
      .out(jsonBody[TgJsonSuccess[FindMilestoneStepTemplateResp]])

  val findByProjectIdEndpoint
    : TgSecuredEndpoint[(UUID, PageRequest), PaginatedResponse[FindMilestoneStepTemplateResp]] =
    securedEndpoint.endpoint.get
      .in(basePath / "project" / path[UUID]("id"))
      .in(pageQuery)
      .out(jsonBody[TgJsonSuccess[PaginatedResponse[FindMilestoneStepTemplateResp]]])

  val listVersionsEndpoint
    : TgSecuredEndpoint[(UUID, PageRequest), PaginatedResponse[MilestoneStepTemplateVersionDto]] =
    securedEndpoint.endpoint.get
      .in(basePath / path[UUID]("id") / "version")
      .in(pageQuery)
      .out(jsonBody[TgJsonSuccess[PaginatedResponse[MilestoneStepTemplateVersionDto]]])

  val deleteByIdEndpoint: TgSecuredEndpointEmptyBody[UUID] =
    securedEndpoint.endpoint.delete
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[TgEmptyBody]])

}
