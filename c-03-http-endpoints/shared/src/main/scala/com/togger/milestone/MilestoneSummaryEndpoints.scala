package com.togger.milestone

import com.togger.auth.SecuredEndpoint
import com.togger.common.TgEndpoints.{TgSecuredEndpoint, TgSecuredEndpointEmptyBody}
import com.togger.common.PaginationInputs.pageQuery
import com.togger.common.{PageRequest, PaginatedResponse, TgJsonSuccess}
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.dto.{
  CreateMilestoneSummaryReq,
  FindMilestoneSummaryResp,
  MilestoneSummaryVersionDto,
  UpdateMilestoneSummaryReq
}
import io.circe.generic.auto._
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

import java.util.UUID

class MilestoneSummaryEndpoints(securedEndpoint: SecuredEndpoint) {

  private val basePath: EndpointInput[Unit] = "milestone-summary"

  val saveEndpoint: TgSecuredEndpoint[CreateMilestoneSummaryReq, FindMilestoneSummaryResp] =
    securedEndpoint.endpoint.post
      .in(basePath)
      .in(jsonBody[CreateMilestoneSummaryReq])
      .out(jsonBody[TgJsonSuccess[FindMilestoneSummaryResp]])

  val findByIdEndpoint: TgSecuredEndpoint[UUID, FindMilestoneSummaryResp] =
    securedEndpoint.endpoint.get
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[FindMilestoneSummaryResp]])

  val updateEndpoint: TgSecuredEndpoint[UpdateMilestoneSummaryReq, FindMilestoneSummaryResp] =
    securedEndpoint.endpoint.put
      .in(basePath)
      .in(jsonBody[UpdateMilestoneSummaryReq])
      .out(jsonBody[TgJsonSuccess[FindMilestoneSummaryResp]])

  val findByMilestoneIdEndpoint: TgSecuredEndpoint[(UUID, PageRequest), PaginatedResponse[FindMilestoneSummaryResp]] =
    securedEndpoint.endpoint.get
      .in(basePath / "milestone" / path[UUID]("id"))
      .in(pageQuery)
      .out(jsonBody[TgJsonSuccess[PaginatedResponse[FindMilestoneSummaryResp]]])

  val findByProjectIdEndpoint: TgSecuredEndpoint[(UUID, PageRequest), PaginatedResponse[FindMilestoneSummaryResp]] =
    securedEndpoint.endpoint.get
      .in(basePath / "project" / path[UUID]("id"))
      .in(pageQuery)
      .out(jsonBody[TgJsonSuccess[PaginatedResponse[FindMilestoneSummaryResp]]])

  val deleteByIdEndpoint: TgSecuredEndpointEmptyBody[UUID] =
    securedEndpoint.endpoint.delete
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[TgEmptyBody]])

  val listVersionsEndpoint: TgSecuredEndpoint[(UUID, PageRequest), PaginatedResponse[MilestoneSummaryVersionDto]] =
    securedEndpoint.endpoint.get
      .in(basePath / path[UUID]("id") / "version")
      .in(pageQuery)
      .out(jsonBody[TgJsonSuccess[PaginatedResponse[MilestoneSummaryVersionDto]]])

  val updateVersionEndpoint: TgSecuredEndpoint[MilestoneSummaryVersionDto, MilestoneSummaryVersionDto] =
    securedEndpoint.endpoint.put
      .in(basePath / "version")
      .in(jsonBody[MilestoneSummaryVersionDto])
      .out(jsonBody[TgJsonSuccess[MilestoneSummaryVersionDto]])

  val deleteVersionEndpoint: TgSecuredEndpointEmptyBody[UUID] =
    securedEndpoint.endpoint.delete
      .in(basePath / "version" / path[UUID]("version"))
      .out(jsonBody[TgJsonSuccess[TgEmptyBody]])

}
