package com.togger.ui.dataimportconfig

import cats.effect.IO
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.{PageRequest, PaginatedResponse}
import com.togger.dto.dataimportconfig.{CreateDataImportConfigReq, DataImportConfigResp, UpdateDataImportConfigReq}
import com.togger.preview._
import com.togger.ui.components.pagination.PaginatedLoader

import java.util.UUID

class DataImportConfigFeServicePreview(ctx: PreviewContext, projects: ProjectsPreviewData, data: DataSyncPreviewData)
    extends DataImportConfigFeService {

  override def create(dto: CreateDataImportConfigReq): TgRespIO[DataImportConfigResp] =
    PreviewResponses.authenticated(ctx.loginRepo)(accountId =>
      IO.defer(
        validated(dto.projectId, dto.name, dto.importPath) {
            val now = ctx.clock.currentInstant
            PreviewResponses.ok(
              data.importConfigs.upsert(
                DataImportConfigResp(
                  UUID.randomUUID(),
                  dto.name.trim,
                  dto.projectId,
                  accountId,
                  dto.importPath.trim,
                  now,
                  now
                )
              )
            )
        }
      )
    )

  override def findById(id: UUID): TgRespIO[DataImportConfigResp] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ => IO.defer(PreviewResponses.fromOption(data.importConfigs.find(id))))

  override def update(dto: UpdateDataImportConfigReq): TgRespIO[DataImportConfigResp] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer(
        if (data.importConfigs.find(dto.id).isEmpty) PreviewResponses.notFound[DataImportConfigResp]
        else
          validated(dto.projectId, dto.name, dto.importPath) {
              PreviewResponses.fromOption(
                data.importConfigs.update(dto.id)(
                  _.copy(
                    projectId = dto.projectId,
                    name = dto.name.trim,
                    importPath = dto.importPath.trim,
                    updatedAt = ctx.clock.currentInstant
                  )
                )
              )
          }
      )
    )

  override def deleteById(id: UUID): TgRespIO[TgEmptyBody] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer(
        if (data.importConfigs.delete(id)) PreviewResponses.okEmpty("Data import config deleted successfully")
        else PreviewResponses.notFound[TgEmptyBody]
      )
    )

  override def findByProjectId(
    projectId: UUID,
    page: PageRequest
  ): TgRespIO[PaginatedResponse[DataImportConfigResp]] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer(
        PreviewResponses.paged(
          data.importConfigs.filter(_.projectId == projectId).sortBy(config => (config.name, config.id.toString)),
          page
        )
      )
    )

  override def findAllByProjectId(projectId: UUID): TgRespIO[Seq[DataImportConfigResp]] =
    PaginatedLoader.loadAll(findByProjectId(projectId, _))

  private def validated(projectId: UUID, name: String, importPath: String)(
    onValid: => TgRespIO[DataImportConfigResp]
  ): TgRespIO[DataImportConfigResp] =
    validate(projectId, name, importPath).fold(onValid)(error => PreviewResponses.badRequest[DataImportConfigResp](error))

  private def validate(projectId: UUID, name: String, importPath: String): Option[String] =
    if (projects.projects.find(projectId).isEmpty) Some("The project does not exist")
    else if (name.trim.isEmpty) Some("The name must not be empty")
    else if (importPath.trim.isEmpty) Some("The import path must not be empty")
    else None
}
