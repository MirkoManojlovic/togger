package com.togger.controllers.dataimportconfig

import com.togger.common.{TgClock, TgJsonError, TgJsonFailure, TgJsonSuccess}
import com.togger.controllers.common.PageRequestValidation
import com.togger.dto.dataimportconfig.DataImportConfigResp
import com.togger.dump.DataImportConfigVerification
import com.togger.dataimportconfig.{DataImportConfig, DataImportConfigEndpoints, DataImportConfigService}

import java.util.UUID

class DataImportConfigController(
  dataImportConfigEndpoints: DataImportConfigEndpoints,
  dataImportConfigService: DataImportConfigService,
  dataImportConfigVerification: DataImportConfigVerification,
  tgClock: TgClock
) {

  import dataImportConfigEndpoints._

  private def toResp(dataImportConfig: DataImportConfig): DataImportConfigResp =
    DataImportConfigResp(
      id = dataImportConfig.id,
      name = dataImportConfig.name,
      projectId = dataImportConfig.projectId,
      accountId = dataImportConfig.accountId,
      importPath = dataImportConfig.importPath,
      createdAt = dataImportConfig.createdAt,
      updatedAt = dataImportConfig.updatedAt
    )

  val findById =
    findByIdEndpoint.serverLogic { auth => id =>
      dataImportConfigService
        .findById(id)
        .map(dataImportConfig => TgJsonSuccess.justBodyRight(toResp(dataImportConfig)))
    }

  val save = saveEndpoint.serverLogic { auth => dto =>
    dataImportConfigVerification
      .verify(dto.name, dto.importPath)
      .fold(
        errors => TgJsonFailure.badRequestLeftIO(TgJsonError.fromErrorsNec(errors)),
        _ => {
          val now = tgClock.currentInstant

          val dataImportConfig = DataImportConfig(
            id = UUID.randomUUID(),
            name = dto.name,
            importPath = dto.importPath,
            projectId = dto.projectId,
            accountId = auth.body.get.id,
            createdAt = now,
            updatedAt = now
          )

          dataImportConfigService
            .create(dataImportConfig)
            .map(saved => TgJsonSuccess.justBodyRight(toResp(saved)))
        }
      )

  }

  val update = updateEndpoint.serverLogic { auth => dto =>
    dataImportConfigVerification
      .verify(dto.name, dto.importPath)
      .fold(
        errors => TgJsonFailure.badRequestLeftIO(TgJsonError.fromErrorsNec(errors)),
        _ => {
          val now = tgClock.currentInstant

          val dataImportConfig = DataImportConfig(
            id = dto.id,
            name = dto.name,
            importPath = dto.importPath,
            projectId = dto.projectId,
            accountId = auth.body.get.id,
            createdAt = now,
            updatedAt = now
          )

          dataImportConfigService
            .update(dataImportConfig)
            .map(updated => TgJsonSuccess.justBodyRight(toResp(updated)))
        }
      )

  }

  val delete = deleteByIdEndpoint.serverLogic { auth => id =>
    dataImportConfigService
      .deleteById(id)
      .map(_ => TgJsonSuccess.justMessageRight("Import data config deleted successfully"))
  }

  val findByProjectId =
    findByProjectIdEndpoint.serverLogic { auth => request =>
      val (projectId, pageRequest) = request
      PageRequestValidation.withPage(pageRequest) { page =>
        dataImportConfigService
          .findPaginatedByProjectId(projectId, page)
          .map(configsPage => TgJsonSuccess.justBodyRight(configsPage.mapItem(toResp)))
      }
    }

}
