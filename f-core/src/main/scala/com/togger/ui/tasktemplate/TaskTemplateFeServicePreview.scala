package com.togger.ui.tasktemplate

import cats.effect.IO
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.{PageRequest, PaginatedResponse}
import com.togger.dto._
import com.togger.preview._

import java.util.UUID

class TaskTemplateFeServicePreview(ctx: PreviewContext, data: TaskConfigPreviewData) extends TaskTemplateFeService {

  private val LabelAlreadyHasTemplate = "The task label is already linked to another template"
  private val LabelNotInProject = "The task label does not belong to the project of the template"
  private val NotFoundMessage = "Task template not found"

  override def createTaskTemplate(dto: CreateTaskTemplateReq): TgRespIO[FindTaskTemplateResp] =
    PreviewResponses.authenticated(ctx.loginRepo)(callerId =>
      IO.defer(
        labelError(dto.projectId, dto.taskLabelId, None).fold {
          val template = PreviewTaskTemplate(UUID.randomUUID(), dto.projectId, dto.taskLabelId, callerId, ctx.clock.currentInstant)
          data.templates.upsert(template)
          data.templateVersions.upsert(newVersion(template.id, dto.name, dto.body))
          PreviewResponses.ok(toDto(template))
        }(PreviewResponses.badRequest[FindTaskTemplateResp](_))
      )
    )

  override def updateTaskTemplate(dto: UpdateTaskTemplateReq): TgRespIO[FindTaskTemplateResp] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer(data.templates.find(dto.id) match {
        case None => PreviewResponses.badRequest[FindTaskTemplateResp](NotFoundMessage)
        case Some(template) =>
          labelError(template.projectId, dto.taskLabelId, Some(template.id)).fold {
            val updated = data.templates.upsert(template.copy(taskLabelId = dto.taskLabelId))
            val unchanged = data.latestVersion(template.id).exists(v => v.name == dto.name && v.body == dto.body)
            if (!unchanged) data.templateVersions.upsert(newVersion(template.id, dto.name, dto.body))
            PreviewResponses.ok(toDto(updated))
          }(PreviewResponses.badRequest[FindTaskTemplateResp](_))
      })
    )

  override def findByProjectId(
    projectId: UUID,
    page: PageRequest
  ): TgRespIO[PaginatedResponse[FindTaskTemplateResp]] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer {
        val templates = data.templates
          .filter(_.projectId == projectId)
          .flatMap(template => toDtoOpt(template))
          .sortBy(template => (template.name, template.id.toString))
        PreviewResponses.paged(templates, page)
      }
    )

  override def findTaskTemplateById(id: UUID): TgRespIO[FindTaskTemplateResp] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer(
        data.templates.find(id).flatMap(toDtoOpt).fold(PreviewResponses.badRequest[FindTaskTemplateResp](NotFoundMessage))(
          PreviewResponses.ok(_)
        )
      )
    )

  override def listVersions(id: UUID, page: PageRequest): TgRespIO[PaginatedResponse[TaskTemplateVersionDto]] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer(PreviewResponses.paged(data.templateVersions.filter(_.taskTemplateId == id).reverse, page))
    )

  override def deleteTaskTemplateById(id: UUID): TgRespIO[TgEmptyBody] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer {
        if (data.templates.find(id).isEmpty) PreviewResponses.badRequest[TgEmptyBody](NotFoundMessage)
        else {
          data.deleteTemplate(id)
          PreviewResponses.okEmpty("Task template deleted successfully")
        }
      }
    )

  private def labelError(projectId: UUID, labelId: Option[UUID], templateId: Option[UUID]): Option[String] =
    labelId.flatMap { id =>
      data.labels.find(id) match {
        case Some(label) if label.projectId == projectId =>
          Option.when(data.templates.all.exists(t => t.taskLabelId.contains(id) && !templateId.contains(t.id)))(
            LabelAlreadyHasTemplate
          )
        case _ => Some(LabelNotInProject)
      }
    }

  private def newVersion(templateId: UUID, name: String, body: String): TaskTemplateVersionDto =
    TaskTemplateVersionDto(UUID.randomUUID(), templateId, name, body, ctx.clock.currentInstant)

  private def toDtoOpt(template: PreviewTaskTemplate): Option[FindTaskTemplateResp] =
    data.latestVersion(template.id).map { version =>
      FindTaskTemplateResp(
        id = template.id,
        projectId = template.projectId,
        name = version.name,
        body = version.body,
        taskLabelId = template.taskLabelId,
        taskLabelName = template.taskLabelId.flatMap(data.labels.find).map(_.name),
        latestVersion = version.version,
        updatedAt = version.createdAt
      )
    }

  private def toDto(template: PreviewTaskTemplate): FindTaskTemplateResp = toDtoOpt(template).get
}
