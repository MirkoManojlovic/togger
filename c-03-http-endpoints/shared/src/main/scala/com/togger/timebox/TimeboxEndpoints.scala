package com.togger.timebox

import com.togger.auth.SecuredEndpoint
import com.togger.common.TgEndpoints.{TgSecuredEndpoint, TgSecuredEndpointEmptyBody}
import com.togger.common.PaginationInputs.pageQuery
import com.togger.common.{PageRequest, PaginatedResponse, TgJsonSuccess}
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.dto.{CreateTimeboxReq, FindTimeboxResp, SaveTimeboxVersionReq, TimeboxVersionDto}
import io.circe.generic.auto._
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

import java.util.UUID

/** Timebox versions are immutable: there is no endpoint to update or delete a single version. */
class TimeboxEndpoints(securedEndpoint: SecuredEndpoint) {

  private val basePath: EndpointInput[Unit] = "timebox"

  val saveEndpoint: TgSecuredEndpoint[CreateTimeboxReq, FindTimeboxResp] =
    securedEndpoint.endpoint.post
      .in(basePath)
      .in(jsonBody[CreateTimeboxReq])
      .out(jsonBody[TgJsonSuccess[FindTimeboxResp]])

  val saveNewVersionEndpoint: TgSecuredEndpoint[SaveTimeboxVersionReq, FindTimeboxResp] =
    securedEndpoint.endpoint.put
      .in(basePath)
      .in(jsonBody[SaveTimeboxVersionReq])
      .out(jsonBody[TgJsonSuccess[FindTimeboxResp]])

  val findByIdEndpoint: TgSecuredEndpoint[UUID, FindTimeboxResp] =
    securedEndpoint.endpoint.get
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[FindTimeboxResp]])

  val findByProjectIdEndpoint: TgSecuredEndpoint[(UUID, PageRequest), PaginatedResponse[FindTimeboxResp]] =
    securedEndpoint.endpoint.get
      .in(basePath / "project" / path[UUID]("id"))
      .in(pageQuery)
      .out(jsonBody[TgJsonSuccess[PaginatedResponse[FindTimeboxResp]]])

  val listVersionsEndpoint: TgSecuredEndpoint[UUID, List[TimeboxVersionDto]] =
    securedEndpoint.endpoint.get
      .in(basePath / path[UUID]("id") / "version")
      .out(jsonBody[TgJsonSuccess[List[TimeboxVersionDto]]])

  val deleteByIdEndpoint: TgSecuredEndpointEmptyBody[UUID] =
    securedEndpoint.endpoint.delete
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[TgEmptyBody]])

}
