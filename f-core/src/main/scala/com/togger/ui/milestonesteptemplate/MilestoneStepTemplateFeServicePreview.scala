package com.togger.ui.milestonesteptemplate

import cats.effect.IO
import com.togger.common.PageRequest
import com.togger.common.PaginatedResponse
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.dto._
import com.togger.preview._

import java.util.UUID

class MilestoneStepTemplateFeServicePreview(ctx: PreviewContext, data: MilestoneStepTemplatesPreviewData)
    extends MilestoneStepTemplateFeService {

  private val NotFoundMessage = "Milestone step template not found"

  override def createMilestoneStepTemplate(
    dto: CreateMilestoneStepTemplateReq
  ): TgRespIO[FindMilestoneStepTemplateResp] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer {
        val template = data.templates.upsert(
          PreviewMilestoneStepTemplate(UUID.randomUUID(), dto.projectId, ctx.clock.currentInstant)
        )
        val _ = data.templateVersions.upsert(newVersion(template.id, dto.name, dto.description))
        PreviewResponses.ok(toDto(template))
      }
    )

  override def updateMilestoneStepTemplate(
    dto: UpdateMilestoneStepTemplateReq
  ): TgRespIO[FindMilestoneStepTemplateResp] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer(data.templates.find(dto.id) match {
        case None => PreviewResponses.badRequest[FindMilestoneStepTemplateResp](NotFoundMessage)
        case Some(template) =>
          val unchanged =
            data.latestVersion(template.id).exists(v => v.name == dto.name && v.description == dto.description)
          if (!unchanged) {
            val _ = data.templateVersions.upsert(newVersion(template.id, dto.name, dto.description))
          }
          PreviewResponses.ok(toDto(template))
      })
    )

  override def findByProjectId(
    projectId: UUID,
    page: PageRequest
  ): TgRespIO[PaginatedResponse[FindMilestoneStepTemplateResp]] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer {
        val templates = data.templates
          .filter(_.projectId == projectId)
          .flatMap(toDtoOpt)
          .sortBy(template => (template.name, template.id.toString))
        PreviewResponses.paged(templates, page)
      }
    )

  override def findMilestoneStepTemplateById(id: UUID): TgRespIO[FindMilestoneStepTemplateResp] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer(
        data.templates
          .find(id)
          .flatMap(toDtoOpt)
          .fold(PreviewResponses.badRequest[FindMilestoneStepTemplateResp](NotFoundMessage))(PreviewResponses.ok(_))
      )
    )

  override def listVersions(
    id: UUID,
    page: PageRequest
  ): TgRespIO[PaginatedResponse[MilestoneStepTemplateVersionDto]] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer(PreviewResponses.paged(data.templateVersions.filter(_.milestoneStepTemplateId == id).reverse, page))
    )

  override def deleteMilestoneStepTemplateById(id: UUID): TgRespIO[TgEmptyBody] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer {
        val usedBy = data.namesBlockingDeletion(id)
        if (usedBy.nonEmpty)
          PreviewResponses.badRequest[TgEmptyBody](
            s"The milestone step template is used by the milestone templates: ${usedBy.mkString(", ")}"
          )
        else if (data.templates.find(id).isEmpty) PreviewResponses.badRequest[TgEmptyBody](NotFoundMessage)
        else {
          val _ = data.deleteTemplate(id)
          PreviewResponses.okEmpty("Milestone step template deleted successfully")
        }
      }
    )

  private def newVersion(templateId: UUID, name: String, description: String): MilestoneStepTemplateVersionDto =
    MilestoneStepTemplateVersionDto(UUID.randomUUID(), templateId, name, description, ctx.clock.currentInstant)

  private def toDtoOpt(template: PreviewMilestoneStepTemplate): Option[FindMilestoneStepTemplateResp] =
    data.latestVersion(template.id).map { version =>
      FindMilestoneStepTemplateResp(
        id = template.id,
        projectId = template.projectId,
        name = version.name,
        description = version.description,
        latestVersion = version.version,
        updatedAt = version.createdAt
      )
    }

  private def toDto(template: PreviewMilestoneStepTemplate): FindMilestoneStepTemplateResp = toDtoOpt(template).get
}
