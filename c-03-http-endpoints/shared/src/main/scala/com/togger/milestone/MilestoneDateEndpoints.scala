package com.togger.milestone

import com.togger.auth.SecuredEndpoint
import com.togger.common.TgEndpoints.{TgSecuredEndpoint, TgSecuredEndpointEmptyBody}
import com.togger.common.TgJsonSuccess
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

  val findByMilestoneIdEndpoint: TgSecuredEndpoint[UUID, Seq[FindMilestoneDateResp]] =
    securedEndpoint.endpoint.get
      .in(basePath / "milestone" / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[Seq[FindMilestoneDateResp]]])

  val deleteByIdEndpoint: TgSecuredEndpointEmptyBody[UUID] =
    securedEndpoint.endpoint.delete
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[TgEmptyBody]])

  val listVersionsEndpoint: TgSecuredEndpoint[UUID, List[MilestoneDateVersionDto]] =
    securedEndpoint.endpoint.get
      .in(basePath / path[UUID]("id") / "version")
      .out(jsonBody[TgJsonSuccess[List[MilestoneDateVersionDto]]])

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
