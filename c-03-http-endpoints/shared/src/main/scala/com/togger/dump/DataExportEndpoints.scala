package com.togger.dump

import com.togger.auth.SecuredEndpoint
import com.togger.common.TgEndpoints.TgSecuredEndpoint
import com.togger.common.TgJsonSuccess
import com.togger.dto.DataExportResp
import com.togger.dto.DataExportStatusResp
import com.togger.dto.DataImportReq
import com.togger.dto.DataImportResp
import com.togger.dto.DataSyncPendingResp
import com.togger.dto.DataSyncResp
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

  /** Creates a project in the organization from the tree at the import path and imports everything into it. */
  val importNewProjectEndpoint: TgSecuredEndpoint[(UUID, DataImportReq), DataImportResp] =
    securedEndpoint.endpoint.post
      .in(basePath / "import-new-project" / path[UUID]("organization-id"))
      .in(jsonBody[DataImportReq])
      .out(jsonBody[TgJsonSuccess[DataImportResp]])

  val checkImportStatusEndpoint: TgSecuredEndpoint[(UUID, DataImportReq), DataExportStatusResp] =
    securedEndpoint.endpoint.post
      .in(basePath / "import" / "status" / path[UUID]("project-id"))
      .in(jsonBody[DataImportReq])
      .out(jsonBody[TgJsonSuccess[DataExportStatusResp]])

  // polled by the frontend
  val exportPendingEndpoint: TgSecuredEndpoint[UUID, DataSyncPendingResp] =
    securedEndpoint.endpoint.get
      .in(basePath / "pending" / path[UUID]("project-id"))
      .out(jsonBody[TgJsonSuccess[DataSyncPendingResp]])

  val importPendingEndpoint: TgSecuredEndpoint[UUID, DataSyncPendingResp] =
    securedEndpoint.endpoint.get
      .in(basePath / "import" / "pending" / path[UUID]("project-id"))
      .out(jsonBody[TgJsonSuccess[DataSyncPendingResp]])

  /** Imports every import path and then exports every export path of all the projects of the account. */
  val syncEndpoint: TgSecuredEndpoint[Unit, DataSyncResp] =
    securedEndpoint.endpoint.post
      .in(basePath / "sync" / "all")
      .out(jsonBody[TgJsonSuccess[DataSyncResp]])

}
