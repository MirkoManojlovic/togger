package com.togger.milestone

import com.togger.auth.SecuredEndpoint
import com.togger.common.TgEndpoints.{TgSecuredEndpoint, TgSecuredEndpointEmptyBody}
import com.togger.common.PaginationInputs.pageQuery
import com.togger.common.{PageRequest, PaginatedResponse, TgJsonSuccess}
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.dto.{CreateMilestoneDateReq, FindMilestoneDateResp, MilestoneDateVersionDto, UpdateMilestoneDateReq}
import io.circe.generic.auto._
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

import java.util.UUID

class MilestoneDateEndpoints(securedEndpoint: SecuredEndpoint) {

  private val basePath: EndpointInput[Unit] = "milestone-date"

  val saveEndpoint: TgSecuredEndpoint[CreateMilestoneDateReq, FindMilestoneDateResp] =
    securedEndpoint.endpoint.post
      .in(basePath)
      .in(jsonBody[CreateMilestoneDateReq])
      .out(jsonBody[TgJsonSuccess[FindMilestoneDateResp]])

  val findByIdEndpoint: TgSecuredEndpoint[UUID, FindMilestoneDateResp] =
    securedEndpoint.endpoint.get
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[FindMilestoneDateResp]])

  val updateEndpoint: TgSecuredEndpoint[UpdateMilestoneDateReq, FindMilestoneDateResp] =
    securedEndpoint.endpoint.put
      .in(basePath)
      .in(jsonBody[UpdateMilestoneDateReq])
      .out(jsonBody[TgJsonSuccess[FindMilestoneDateResp]])

  val findByMilestoneIdEndpoint: TgSecuredEndpoint[(UUID, PageRequest), PaginatedResponse[FindMilestoneDateResp]] =
    securedEndpoint.endpoint.get
      .in(basePath / "milestone" / path[UUID]("id"))
      .in(pageQuery)
      .out(jsonBody[TgJsonSuccess[PaginatedResponse[FindMilestoneDateResp]]])

  val findByProjectIdEndpoint: TgSecuredEndpoint[(UUID, PageRequest), PaginatedResponse[FindMilestoneDateResp]] =
    securedEndpoint.endpoint.get
      .in(basePath / "project" / path[UUID]("id"))
      .in(pageQuery)
      .out(jsonBody[TgJsonSuccess[PaginatedResponse[FindMilestoneDateResp]]])

  val deleteByIdEndpoint: TgSecuredEndpointEmptyBody[UUID] =
    securedEndpoint.endpoint.delete
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[TgEmptyBody]])

  val listVersionsEndpoint: TgSecuredEndpoint[(UUID, PageRequest), PaginatedResponse[MilestoneDateVersionDto]] =
    securedEndpoint.endpoint.get
      .in(basePath / path[UUID]("id") / "version")
      .in(pageQuery)
      .out(jsonBody[TgJsonSuccess[PaginatedResponse[MilestoneDateVersionDto]]])

  val updateVersionEndpoint: TgSecuredEndpoint[MilestoneDateVersionDto, MilestoneDateVersionDto] =
    securedEndpoint.endpoint.put
      .in(basePath / "version")
      .in(jsonBody[MilestoneDateVersionDto])
      .out(jsonBody[TgJsonSuccess[MilestoneDateVersionDto]])

  val deleteVersionEndpoint: TgSecuredEndpointEmptyBody[UUID] =
    securedEndpoint.endpoint.delete
      .in(basePath / "version" / path[UUID]("version"))
      .out(jsonBody[TgJsonSuccess[TgEmptyBody]])

}
