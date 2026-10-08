package com.togger.milestone

import com.togger.auth.SecuredEndpoint
import com.togger.common.TgEndpoints.{TgSecuredEndpoint, TgSecuredEndpointEmptyBody}
import com.togger.common.PaginationInputs.pageQuery
import com.togger.common.{PageRequest, PaginatedResponse, TgJsonSuccess}
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.dto.{CreateMilestoneReq, FindMilestoneResp, MilestoneFilter, MilestoneVersionDto, UpdateMilestoneReq}
import io.circe.generic.auto._
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

import java.util.UUID

class MilestoneEndpoints(securedEndpoint: SecuredEndpoint) {

  private val basePath: EndpointInput[Unit] = "milestone"

  val saveEndpoint: TgSecuredEndpoint[CreateMilestoneReq, FindMilestoneResp] =
    securedEndpoint.endpoint.post
      .in(basePath)
      .in(jsonBody[CreateMilestoneReq])
      .out(jsonBody[TgJsonSuccess[FindMilestoneResp]])

  val findByIdEndpoint: TgSecuredEndpoint[UUID, FindMilestoneResp] =
    securedEndpoint.endpoint.get
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[FindMilestoneResp]])

  val updateEndpoint: TgSecuredEndpoint[UpdateMilestoneReq, FindMilestoneResp] =
    securedEndpoint.endpoint.put
      .in(basePath)
      .in(jsonBody[UpdateMilestoneReq])
      .out(jsonBody[TgJsonSuccess[FindMilestoneResp]])

  val findByProjectIdEndpoint
    : TgSecuredEndpoint[(UUID, MilestoneFilter, PageRequest), PaginatedResponse[FindMilestoneResp]] =
    securedEndpoint.endpoint.get
      .in(basePath / "project" / path[UUID]("id"))
      .in(MilestoneFilterInputs.milestoneFilterQuery)
      .in(pageQuery)
      .out(jsonBody[TgJsonSuccess[PaginatedResponse[FindMilestoneResp]]])

  val deleteByIdEndpoint: TgSecuredEndpointEmptyBody[UUID] =
    securedEndpoint.endpoint.delete
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[TgEmptyBody]])

  val listVersionsEndpoint: TgSecuredEndpoint[UUID, List[MilestoneVersionDto]] =
    securedEndpoint.endpoint.get
      .in(basePath / path[UUID]("id") / "version")
      .out(jsonBody[TgJsonSuccess[List[MilestoneVersionDto]]])

  val updateVersionEndpoint: TgSecuredEndpoint[MilestoneVersionDto, MilestoneVersionDto] =
    securedEndpoint.endpoint.put
      .in(basePath / "version")
      .in(jsonBody[MilestoneVersionDto])
      .out(jsonBody[TgJsonSuccess[MilestoneVersionDto]])

  val deleteVersionEndpoint: TgSecuredEndpointEmptyBody[UUID] =
    securedEndpoint.endpoint.delete
      .in(basePath / "version" / path[UUID]("version"))
      .out(jsonBody[TgJsonSuccess[TgEmptyBody]])

}
