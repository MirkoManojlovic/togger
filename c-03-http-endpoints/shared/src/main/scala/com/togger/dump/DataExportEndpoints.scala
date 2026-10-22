package com.togger.dump

import com.togger.auth.SecuredEndpoint
import com.togger.common.TgEndpoints.TgSecuredEndpoint
import com.togger.common.TgJsonSuccess
import com.togger.dto.{DataExportResp, DataExportStatusResp, DataImportReq, DataImportResp}
import io.circe.generic.auto._
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

import java.util.UUID

class DataExportEndpoints(securedEndpoint: SecuredEndpoint) {

  private val basePath: EndpointInput[Unit] = "project" / "export"

  val exportEndpoint: TgSecuredEndpoint[UUID, DataExportResp] =
    securedEndpoint.endpoint.post
      .in(basePath / path[UUID]("project-id"))
      .out(jsonBody[TgJsonSuccess[DataExportResp]])

  val checkStatusEndpoint: TgSecuredEndpoint[UUID, DataExportStatusResp] =
    securedEndpoint.endpoint.get
      .in(basePath / "status" / path[UUID]("project-id"))
      .out(jsonBody[TgJsonSuccess[DataExportStatusResp]])

  val exportByConfigEndpoint: TgSecuredEndpoint[UUID, DataExportResp] =
    securedEndpoint.endpoint.post
      .in(basePath / "config" / path[UUID]("export-config-id"))
      .out(jsonBody[TgJsonSuccess[DataExportResp]])

  val checkStatusByConfigEndpoint: TgSecuredEndpoint[UUID, DataExportStatusResp] =
    securedEndpoint.endpoint.get
      .in(basePath / "status" / "config" / path[UUID]("export-config-id"))
      .out(jsonBody[TgJsonSuccess[DataExportStatusResp]])

  val importEndpoint: TgSecuredEndpoint[(UUID, DataImportReq), DataImportResp] =
    securedEndpoint.endpoint.post
      .in(basePath / "import" / path[UUID]("project-id"))
      .in(jsonBody[DataImportReq])
      .out(jsonBody[TgJsonSuccess[DataImportResp]])

}
