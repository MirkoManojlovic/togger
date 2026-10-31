package com.togger.ui.task

import cats.effect.IO
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.{PageRequest, PaginatedResponse}
import com.togger.dto._
import com.togger.preview._
import com.togger.task.DefaultTaskStatus

import java.util.UUID

class TaskStatusFeServicePreview(
  ctx: PreviewContext,
  projects: ProjectsPreviewData,
  data: TaskConfigPreviewData,
  stats: TaskStatsPreviewData
) extends TaskStatusFeService {

  override def createTaskStatus(dto: CreateTaskStatusReq): TgRespIO[CreateTaskStatusResp] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer {
        if (projects.projects.find(dto.projectId).isEmpty) PreviewResponses.notFound[CreateTaskStatusResp]
        else {
          val created = data.statuses.upsert(
            FindTaskStatusResp(UUID.randomUUID(), dto.projectId, dto.name, dto.description, dto.emoji)
          )
          PreviewResponses.ok(CreateTaskStatusResp(created.id, created.projectId, created.name, created.description, created.emoji))
        }
      }
    )

  override def updateTaskStatus(dto: UpdateTaskStatusResp): TgRespIO[FindTaskStatusResp] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer(
        PreviewResponses.fromOption(
          data.statuses.update(dto.id)(
            _.copy(projectId = dto.projectId, name = dto.name, description = dto.description, emoji = dto.emoji)
          )
        )
      )
    )

  override def findByProjectId(projectId: UUID, page: PageRequest): TgRespIO[PaginatedResponse[FindTaskStatusResp]] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer(
        PreviewResponses.paged(
          data.statuses.filter(_.projectId == projectId).sortBy(status => (status.name, status.id.toString)),
          page
        )
      )
    )

  override def findTaskStatusById(id: UUID): TgRespIO[FindTaskStatusResp] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ => IO.defer(PreviewResponses.fromOption(data.statuses.find(id))))

  override def deleteTaskStatusById(id: UUID): TgRespIO[TgEmptyBody] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer(data.statuses.find(id) match {
        case None => PreviewResponses.notFound[TgEmptyBody]
        case Some(status) if status.name == DefaultTaskStatus.Done.statusName =>
          PreviewResponses.badRequest[TgEmptyBody](s"Task status ${DefaultTaskStatus.Done.statusName} cannot be deleted")
        case Some(_) if stats.source.statusInUse(id) =>
          PreviewResponses.badRequest[TgEmptyBody]("Task status is used by tasks and cannot be deleted")
        case Some(_) =>
          data.statuses.delete(id)
          PreviewResponses.okEmpty("Task status deleted successfully")
      })
    )
}
