package com.togger.ui.projectmapping

import cats.effect.IO
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.{PageRequest, PaginatedResponse}
import com.togger.dto.projectmapping.{CreateProjectMappingReq, ProjectMappingResp}
import com.togger.preview._
import com.togger.ui.components.pagination.PaginatedLoader

import java.util.UUID

class ProjectMappingFeServicePreview(ctx: PreviewContext, projects: ProjectsPreviewData, data: DataSyncPreviewData)
    extends ProjectMappingFeService {

  override def create(dto: CreateProjectMappingReq): TgRespIO[ProjectMappingResp] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer {
        if (projects.projects.find(dto.projectIdDb).isEmpty) PreviewResponses.notFound[ProjectMappingResp]
        else if (find(dto.projectIdDb, dto.projectIdDump).isDefined)
          PreviewResponses.badRequest[ProjectMappingResp]("The project mapping already exists")
        else {
          val now = ctx.clock.currentInstant
          val mapping = ProjectMappingResp(dto.projectIdDb, dto.projectIdDump, now, now)
          data.mappings.upsert(PreviewProjectMapping(UUID.randomUUID(), mapping))
          PreviewResponses.ok(mapping)
        }
      }
    )

  override def findByProjectIdDb(
    projectIdDb: UUID,
    page: PageRequest
  ): TgRespIO[PaginatedResponse[ProjectMappingResp]] =
    paged(_.projectIdDb == projectIdDb, page)

  override def findAllByProjectIdDb(projectIdDb: UUID): TgRespIO[Seq[ProjectMappingResp]] =
    PaginatedLoader.loadAll(findByProjectIdDb(projectIdDb, _))

  override def findByProjectIdDump(
    projectIdDump: UUID,
    page: PageRequest
  ): TgRespIO[PaginatedResponse[ProjectMappingResp]] =
    paged(_.projectIdDump == projectIdDump, page)

  override def deleteByIds(projectIdDb: UUID, projectIdDump: UUID): TgRespIO[TgEmptyBody] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer(
        find(projectIdDb, projectIdDump).fold(PreviewResponses.notFound[TgEmptyBody]) { existing =>
          data.mappings.delete(existing.id)
          PreviewResponses.okEmpty("Project mapping deleted successfully")
        }
      )
    )

  private def find(projectIdDb: UUID, projectIdDump: UUID): Option[PreviewProjectMapping] =
    data.mappings.all.find(m => m.mapping.projectIdDb == projectIdDb && m.mapping.projectIdDump == projectIdDump)

  private def paged(
    predicate: ProjectMappingResp => Boolean,
    page: PageRequest
  ): TgRespIO[PaginatedResponse[ProjectMappingResp]] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer(
        PreviewResponses.paged(
          data.mappings.all.map(_.mapping).filter(predicate).sortBy(m => (m.createdAt, m.projectIdDump.toString)),
          page
        )
      )
    )
}
