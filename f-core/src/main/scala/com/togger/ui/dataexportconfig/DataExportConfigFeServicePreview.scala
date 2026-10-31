package com.togger.ui.dataexportconfig

import cats.effect.IO
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.{PageRequest, PaginatedResponse}
import com.togger.dto.dataexportconfig.{CreateDataExportConfigReq, DataExportConfigResp, UpdateDataExportConfigReq}
import com.togger.preview._
import com.togger.ui.components.pagination.PaginatedLoader

import java.util.UUID

class DataExportConfigFeServicePreview(ctx: PreviewContext, projects: ProjectsPreviewData, data: DataSyncPreviewData)
    extends DataExportConfigFeService {

  override def create(dto: CreateDataExportConfigReq): TgRespIO[DataExportConfigResp] =
    PreviewResponses.authenticated(ctx.loginRepo)(accountId =>
      IO.defer(
        validated(dto.projectId, dto.name, dto.exportPath) {
            val now = ctx.clock.currentInstant
            PreviewResponses.ok(
              data.exportConfigs.upsert(
                DataExportConfigResp(
                  UUID.randomUUID(),
                  dto.projectId,
                  accountId,
                  dto.name.trim,
                  dto.exportPath.trim,
                  now,
                  now
                )
              )
            )
        }
      )
    )

  override def findById(id: UUID): TgRespIO[DataExportConfigResp] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ => IO.defer(PreviewResponses.fromOption(data.exportConfigs.find(id))))

  override def update(dto: UpdateDataExportConfigReq): TgRespIO[DataExportConfigResp] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer(
        if (data.exportConfigs.find(dto.id).isEmpty) PreviewResponses.notFound[DataExportConfigResp]
        else
          validated(dto.projectId, dto.name, dto.exportPath) {
              PreviewResponses.fromOption(
                data.exportConfigs.update(dto.id)(
                  _.copy(
                    projectId = dto.projectId,
                    name = dto.name.trim,
                    exportPath = dto.exportPath.trim,
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
        if (data.exportConfigs.delete(id)) PreviewResponses.okEmpty("Data export config deleted successfully")
        else PreviewResponses.notFound[TgEmptyBody]
      )
    )

  override def findByProjectId(
    projectId: UUID,
    page: PageRequest
  ): TgRespIO[PaginatedResponse[DataExportConfigResp]] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer(
        PreviewResponses.paged(
          data.exportConfigs.filter(_.projectId == projectId).sortBy(config => (config.name, config.id.toString)),
          page
        )
      )
    )

  override def findAllByProjectId(projectId: UUID): TgRespIO[Seq[DataExportConfigResp]] =
    PaginatedLoader.loadAll(findByProjectId(projectId, _))

  private def validated(projectId: UUID, name: String, exportPath: String)(
    onValid: => TgRespIO[DataExportConfigResp]
  ): TgRespIO[DataExportConfigResp] =
    validate(projectId, name, exportPath).fold(onValid)(error => PreviewResponses.badRequest[DataExportConfigResp](error))

  private def validate(projectId: UUID, name: String, exportPath: String): Option[String] =
    if (projects.projects.find(projectId).isEmpty) Some("The project does not exist")
    else if (name.trim.isEmpty) Some("The name must not be empty")
    else if (exportPath.trim.isEmpty) Some("The export path must not be empty")
    else None
}
