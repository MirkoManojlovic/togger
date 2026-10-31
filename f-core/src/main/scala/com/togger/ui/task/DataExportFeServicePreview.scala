package com.togger.ui.task

import cats.effect.IO
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.dto._
import com.togger.preview._

import java.util.UUID

/** Export and import work on files, which a browser cannot reach: the actions fail, the checks report nothing to do. */
class DataExportFeServicePreview(ctx: PreviewContext, projects: ProjectsPreviewData, data: DataSyncPreviewData)
    extends DataExportFeService {
  import DataExportFeServicePreview._

  override def exportData(projectId: UUID): TgRespIO[DataExportResp] = unavailable

  override def exportByConfig(exportConfigId: UUID): TgRespIO[DataExportResp] = unavailable

  override def importData(projectId: UUID, importPath: String): TgRespIO[DataImportResp] = unavailable

  override def importNewProject(organizationId: UUID, importPath: String): TgRespIO[DataImportResp] = unavailable

  override def checkStatus(projectId: UUID): TgRespIO[DataExportStatusResp] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer(
        if (projects.projects.find(projectId).isEmpty) PreviewResponses.notFound[DataExportStatusResp]
        else
          PreviewResponses.ok(
            DataExportStatusResp(
              projectId,
              data.exportConfigs
                .filter(_.projectId == projectId)
                .map(config => DataExportPathStatusResp(config.exportPath, Nil, Some(config.id), Some(config.name)))
            )
          )
      )
    )

  override def checkStatusByConfig(exportConfigId: UUID): TgRespIO[DataExportStatusResp] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer(
        PreviewResponses.fromOption(data.exportConfigs.find(exportConfigId).map { config =>
          DataExportStatusResp(
            config.projectId,
            List(DataExportPathStatusResp(config.exportPath, Nil, Some(config.id), Some(config.name)))
          )
        })
      )
    )

  override def checkImportStatus(projectId: UUID, importPath: String): TgRespIO[DataExportStatusResp] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      PreviewResponses.ok(DataExportStatusResp(projectId, List(DataExportPathStatusResp(importPath, Nil))))
    )

  override def exportPending(projectId: UUID): TgRespIO[DataSyncPendingResp] = nothingPending(projectId)

  override def importPending(projectId: UUID): TgRespIO[DataSyncPendingResp] = nothingPending(projectId)

  private def nothingPending(projectId: UUID): TgRespIO[DataSyncPendingResp] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ => PreviewResponses.ok(DataSyncPendingResp(projectId, false)))
}

object DataExportFeServicePreview {
  val UnavailableMessage = "Export/import is not available in frontend preview mode"

  private def unavailable[A]: TgRespIO[A] = PreviewResponses.badRequest[A](UnavailableMessage)
}
