package com.togger.milestone

import com.togger.auth.SecuredEndpoint
import com.togger.common.PaginationInputs.pageQuery
import com.togger.common.TgEndpoints.{TgSecuredEndpoint, TgSecuredEndpointEmptyBody}
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.{PageRequest, PaginatedResponse, TgJsonSuccess}
import com.togger.dto.{
  CreateMilestoneTemplateReq,
  FindMilestoneTemplateResp,
  MilestoneTemplateVersionDto,
  UpdateMilestoneTemplateReq
}
import io.circe.generic.auto._
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

import java.util.UUID

class MilestoneTemplateEndpoints(securedEndpoint: SecuredEndpoint) {

  private val basePath: EndpointInput[Unit] = "milestone-template"

  val saveEndpoint: TgSecuredEndpoint[CreateMilestoneTemplateReq, FindMilestoneTemplateResp] =
    securedEndpoint.endpoint.post
      .in(basePath)
      .in(jsonBody[CreateMilestoneTemplateReq])
      .out(jsonBody[TgJsonSuccess[FindMilestoneTemplateResp]])

  val findByIdEndpoint: TgSecuredEndpoint[UUID, FindMilestoneTemplateResp] =
    securedEndpoint.endpoint.get
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[FindMilestoneTemplateResp]])

  val updateEndpoint: TgSecuredEndpoint[UpdateMilestoneTemplateReq, FindMilestoneTemplateResp] =
    securedEndpoint.endpoint.put
      .in(basePath)
      .in(jsonBody[UpdateMilestoneTemplateReq])
      .out(jsonBody[TgJsonSuccess[FindMilestoneTemplateResp]])

  val findByProjectIdEndpoint
    : TgSecuredEndpoint[(UUID, PageRequest), PaginatedResponse[FindMilestoneTemplateResp]] =
    securedEndpoint.endpoint.get
      .in(basePath / "project" / path[UUID]("id"))
      .in(pageQuery)
      .out(jsonBody[TgJsonSuccess[PaginatedResponse[FindMilestoneTemplateResp]]])

  val listVersionsEndpoint
    : TgSecuredEndpoint[(UUID, PageRequest), PaginatedResponse[MilestoneTemplateVersionDto]] =
    securedEndpoint.endpoint.get
      .in(basePath / path[UUID]("id") / "version")
      .in(pageQuery)
      .out(jsonBody[TgJsonSuccess[PaginatedResponse[MilestoneTemplateVersionDto]]])

  val deleteByIdEndpoint: TgSecuredEndpointEmptyBody[UUID] =
    securedEndpoint.endpoint.delete
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[TgEmptyBody]])

}
