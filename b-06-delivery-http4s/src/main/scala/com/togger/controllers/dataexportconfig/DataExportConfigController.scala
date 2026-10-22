package com.togger.controllers.dataexportconfig

import com.togger.common.{TgClock, TgJsonError, TgJsonFailure, TgJsonSuccess}
import com.togger.controllers.common.PageRequestValidation
import com.togger.dto.dataexportconfig.DataExportConfigResp
import com.togger.dump.DataImportConfigVerification
import com.togger.dataexportconfig.{DataExportConfig, DataExportConfigEndpoints, DataExportConfigService}

import java.util.UUID

class DataExportConfigController(
  dataExportConfigEndpoints: DataExportConfigEndpoints,
  dataExportConfigService: DataExportConfigService,
  dataExportConfigVerification: DataImportConfigVerification,
  tgClock: TgClock
) {

  import dataExportConfigEndpoints._

  private def toResp(dataExportConfig: DataExportConfig): DataExportConfigResp =
    DataExportConfigResp(
      id = dataExportConfig.id,
      projectId = dataExportConfig.projectId,
      accountId = dataExportConfig.accountId,
      name = dataExportConfig.name,
      exportPath = dataExportConfig.exportPath,
      createdAt = dataExportConfig.createdAt,
      updatedAt = dataExportConfig.updatedAt
    )

  val findById =
    findByIdEndpoint.serverLogic { auth => id =>
      dataExportConfigService
        .findById(id)
        .map(dataExportConfig => TgJsonSuccess.justBodyRight(toResp(dataExportConfig)))
    }

  val save = saveEndpoint.serverLogic { auth => dto =>
    dataExportConfigVerification
      .verify(dto.name, dto.exportPath)
      .fold(
        errors => TgJsonFailure.badRequestLeftIO(TgJsonError.fromErrorsNec(errors)),
        _ => {
          val now = tgClock.currentInstant

          val dataExportConfig = DataExportConfig(
            id = UUID.randomUUID(),
            projectId = dto.projectId,
            accountId = auth.body.get.id,
            name = dto.name,
            exportPath = dto.exportPath,
            createdAt = now,
            updatedAt = now
          )

          dataExportConfigService
            .create(dataExportConfig)
            .map(saved => TgJsonSuccess.justBodyRight(toResp(saved)))
        }
      )

  }

  val update = updateEndpoint.serverLogic { auth => dto =>
    dataExportConfigVerification
      .verify(dto.name, dto.exportPath)
      .fold(
        errors => TgJsonFailure.badRequestLeftIO(TgJsonError.fromErrorsNec(errors)),
        _ => {
          val now = tgClock.currentInstant

          val dataExportConfig = DataExportConfig(
            id = dto.id,
            projectId = dto.projectId,
            accountId = auth.body.get.id,
            name = dto.name,
            exportPath = dto.exportPath,
            createdAt = now,
            updatedAt = now
          )

          dataExportConfigService
            .update(dataExportConfig)
            .map(updated => TgJsonSuccess.justBodyRight(toResp(updated)))
        }
      )

  }

  val delete = deleteByIdEndpoint.serverLogic { auth => id =>
    dataExportConfigService
      .deleteById(id)
      .map(_ => TgJsonSuccess.justMessageRight("Task dump config deleted successfully"))
  }

  val findByProjectId =
    findByProjectIdEndpoint.serverLogic { auth => request =>
      val (projectId, pageRequest) = request
      PageRequestValidation.withPage(pageRequest) { page =>
        dataExportConfigService
          .findPaginatedByProjectId(projectId, page)
          .map(configsPage => TgJsonSuccess.justBodyRight(configsPage.mapItem(toResp)))
      }
    }

}
