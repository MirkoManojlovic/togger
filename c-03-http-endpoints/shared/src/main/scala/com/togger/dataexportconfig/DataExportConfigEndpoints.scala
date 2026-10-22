package com.togger.dataexportconfig

import com.togger.auth.SecuredEndpoint
import com.togger.common.TgEndpoints._
import com.togger.common.PaginationInputs.pageQuery
import com.togger.common.{PageRequest, PaginatedResponse, TgJsonSuccess}
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.dto.dataexportconfig.{CreateDataExportConfigReq, DataExportConfigResp, UpdateDataExportConfigReq}
import io.circe.generic.auto._
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

import java.util.UUID

class DataExportConfigEndpoints(securedEndpoint: SecuredEndpoint) {

  private val basePath: EndpointInput[Unit] = "data-export-config"

  val saveEndpoint: TgSecuredEndpoint[CreateDataExportConfigReq, DataExportConfigResp] =
    securedEndpoint.endpoint.post
      .in(basePath)
      .in(jsonBody[CreateDataExportConfigReq])
      .out(jsonBody[TgJsonSuccess[DataExportConfigResp]])

  val findByIdEndpoint: TgSecuredEndpoint[UUID, DataExportConfigResp] =
    securedEndpoint.endpoint.get
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[DataExportConfigResp]])

  val updateEndpoint: TgSecuredEndpoint[UpdateDataExportConfigReq, DataExportConfigResp] =
    securedEndpoint.endpoint.put
      .in(basePath)
      .in(jsonBody[UpdateDataExportConfigReq])
      .out(jsonBody[TgJsonSuccess[DataExportConfigResp]])

  val deleteByIdEndpoint: TgSecuredEndpointEmptyBody[UUID] =
    securedEndpoint.endpoint.delete
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[TgEmptyBody]])

  val findByProjectIdEndpoint: TgSecuredEndpoint[(UUID, PageRequest), PaginatedResponse[DataExportConfigResp]] =
    securedEndpoint.endpoint.get
      .in(basePath / "project" / path[UUID]("id"))
      .in(pageQuery)
      .out(jsonBody[TgJsonSuccess[PaginatedResponse[DataExportConfigResp]]])

}
