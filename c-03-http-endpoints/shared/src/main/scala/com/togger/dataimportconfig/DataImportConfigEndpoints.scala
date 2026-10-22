package com.togger.dataimportconfig

import com.togger.auth.SecuredEndpoint
import com.togger.common.TgEndpoints._
import com.togger.common.PaginationInputs.pageQuery
import com.togger.common.{PageRequest, PaginatedResponse, TgJsonSuccess}
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.dto.dataimportconfig.{CreateDataImportConfigReq, DataImportConfigResp, UpdateDataImportConfigReq}
import io.circe.generic.auto._
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

import java.util.UUID

class DataImportConfigEndpoints(securedEndpoint: SecuredEndpoint) {

  private val basePath: EndpointInput[Unit] = "data-import-config"

  val saveEndpoint: TgSecuredEndpoint[CreateDataImportConfigReq, DataImportConfigResp] =
    securedEndpoint.endpoint.post
      .in(basePath)
      .in(jsonBody[CreateDataImportConfigReq])
      .out(jsonBody[TgJsonSuccess[DataImportConfigResp]])

  val findByIdEndpoint: TgSecuredEndpoint[UUID, DataImportConfigResp] =
    securedEndpoint.endpoint.get
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[DataImportConfigResp]])

  val updateEndpoint: TgSecuredEndpoint[UpdateDataImportConfigReq, DataImportConfigResp] =
    securedEndpoint.endpoint.put
      .in(basePath)
      .in(jsonBody[UpdateDataImportConfigReq])
      .out(jsonBody[TgJsonSuccess[DataImportConfigResp]])

  val deleteByIdEndpoint: TgSecuredEndpointEmptyBody[UUID] =
    securedEndpoint.endpoint.delete
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[TgEmptyBody]])

  val findByProjectIdEndpoint: TgSecuredEndpoint[(UUID, PageRequest), PaginatedResponse[DataImportConfigResp]] =
    securedEndpoint.endpoint.get
      .in(basePath / "project" / path[UUID]("id"))
      .in(pageQuery)
      .out(jsonBody[TgJsonSuccess[PaginatedResponse[DataImportConfigResp]]])

}
