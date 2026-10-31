package com.togger.ui.task

import cats.effect.IO
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.{PageRequest, PaginatedResponse}
import com.togger.dto._
import com.togger.preview._

import java.util.UUID

class TaskLabelFeServicePreview(ctx: PreviewContext, projects: ProjectsPreviewData, data: TaskConfigPreviewData)
    extends TaskLabelFeService {

  override def createTaskLabel(dto: CreateTaskLabelReq): TgRespIO[CreateTaskLabelResp] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer {
        if (projects.projects.find(dto.projectId).isEmpty) PreviewResponses.notFound[CreateTaskLabelResp]
        else {
          val created =
            data.labels.upsert(FindTaskLabelResp(UUID.randomUUID(), dto.projectId, dto.name, dto.description, dto.emoji))
          PreviewResponses.ok(CreateTaskLabelResp(created.id, created.projectId, created.name, created.description, created.emoji))
        }
      }
    )

  override def updateTaskLabel(dto: UpdateTaskLabelResp): TgRespIO[FindTaskLabelResp] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer(
        PreviewResponses.fromOption(
          data.labels.update(dto.id)(
            _.copy(projectId = dto.projectId, name = dto.name, description = dto.description, emoji = dto.emoji)
          )
        )
      )
    )

  override def findByProjectId(projectId: UUID, page: PageRequest): TgRespIO[PaginatedResponse[FindTaskLabelResp]] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer(
        PreviewResponses.paged(
          data.labels.filter(_.projectId == projectId).sortBy(label => (label.name, label.id.toString)),
          page
        )
      )
    )

  override def findTaskLabelById(id: UUID): TgRespIO[FindTaskLabelResp] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ => IO.defer(PreviewResponses.fromOption(data.labels.find(id))))

  override def deleteTaskLabelById(id: UUID): TgRespIO[TgEmptyBody] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer {
        if (data.labels.find(id).isEmpty) PreviewResponses.notFound[TgEmptyBody]
        else {
          data.deleteLabel(id)
          PreviewResponses.okEmpty("Task label deleted successfully")
        }
      }
    )
}
