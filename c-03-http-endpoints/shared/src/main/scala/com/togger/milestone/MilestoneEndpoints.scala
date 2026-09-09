package com.togger.milestone

import com.togger.auth.SecuredEndpoint
import com.togger.common.TgEndpoints.{TgSecuredEndpoint, TgSecuredEndpointEmptyBody}
import com.togger.common.TgJsonSuccess
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.dto.{CreateMilestoneReq, FindMilestoneResp, MilestoneVersionDto, UpdateMilestoneReq}
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

  val findByProjectIdEndpoint: TgSecuredEndpoint[UUID, Seq[FindMilestoneResp]] =
    securedEndpoint.endpoint.get
      .in(basePath / "project" / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[Seq[FindMilestoneResp]]])

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
