package com.togger.ui.projectlabel

import cats.effect.IO
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.{PageRequest, PaginatedResponse}
import com.togger.dto._
import com.togger.preview._

import java.util.UUID

class ProjectLabelFeServicePreview(ctx: PreviewContext, data: ProjectLabelsPreviewData) extends ProjectLabelFeService {

  override def createProjectLabel(dto: CreateProjectLabelReq): TgRespIO[CreateProjectLabelResp] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer {
        if (data.categories.find(dto.categoryId).isEmpty) PreviewResponses.notFound[CreateProjectLabelResp]
        else {
          val created =
            data.labels.upsert(FindProjectLabelResp(UUID.randomUUID(), dto.name, dto.description, dto.categoryId))
          PreviewResponses.ok(CreateProjectLabelResp(created.id, created.name, created.description, created.categoryId))
        }
      }
    )

  override def updateProjectLabel(dto: UpdateProjectLabelResp): TgRespIO[FindProjectLabelResp] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer {
        if (data.categories.find(dto.categoryId).isEmpty) PreviewResponses.notFound[FindProjectLabelResp]
        else
          PreviewResponses.fromOption(
            data.labels.update(dto.id)(_.copy(name = dto.name, description = dto.description, categoryId = dto.categoryId))
          )
      }
    )

  override def findAll(page: PageRequest): TgRespIO[PaginatedResponse[FindProjectLabelResp]] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer(PreviewResponses.paged(data.labels.all.sortBy(label => (label.name, label.id.toString)), page))
    )

  override def findProjectLabelById(id: UUID): TgRespIO[FindProjectLabelResp] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ => IO.defer(PreviewResponses.fromOption(data.labels.find(id))))

  override def deleteProjectLabelById(id: UUID): TgRespIO[TgEmptyBody] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer {
        if (data.labels.find(id).isEmpty) PreviewResponses.notFound[TgEmptyBody]
        else {
          data.deleteLabel(id)
          PreviewResponses.okEmpty("Project label deleted successfully")
        }
      }
    )
}
