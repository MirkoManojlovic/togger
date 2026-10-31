package com.togger.ui.projectlabel

import cats.effect.IO
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.{PageRequest, PaginatedResponse}
import com.togger.dto._
import com.togger.preview._

import java.util.UUID

class ProjectLabelCategoryFeServicePreview(ctx: PreviewContext, data: ProjectLabelsPreviewData)
    extends ProjectLabelCategoryFeService {

  override def createProjectLabelCategory(
    dto: CreateProjectLabelCategoryReq
  ): TgRespIO[CreateProjectLabelCategoryResp] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer {
        val created = data.categories.upsert(FindProjectLabelCategoryResp(UUID.randomUUID(), dto.name))
        PreviewResponses.ok(CreateProjectLabelCategoryResp(created.id, created.name))
      }
    )

  override def updateProjectLabelCategory(dto: UpdateProjectLabelCategoryResp): TgRespIO[FindProjectLabelCategoryResp] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer(PreviewResponses.fromOption(data.categories.update(dto.id)(_.copy(name = dto.name))))
    )

  override def findAll(page: PageRequest): TgRespIO[PaginatedResponse[FindProjectLabelCategoryResp]] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer(PreviewResponses.paged(data.categories.all.sortBy(category => (category.name, category.id.toString)), page))
    )

  override def findProjectLabelCategoryById(id: UUID): TgRespIO[FindProjectLabelCategoryResp] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ => IO.defer(PreviewResponses.fromOption(data.categories.find(id))))

  override def deleteProjectLabelCategoryById(id: UUID): TgRespIO[TgEmptyBody] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer {
        if (data.categories.find(id).isEmpty) PreviewResponses.notFound[TgEmptyBody]
        else if (data.labels.all.exists(_.categoryId == id))
          PreviewResponses.badRequest[TgEmptyBody]("The category still has project labels and cannot be deleted")
        else {
          data.categories.delete(id)
          PreviewResponses.okEmpty("Project label category deleted successfully")
        }
      }
    )
}
