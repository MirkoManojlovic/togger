package com.togger.ui.milestonetemplate

import cats.effect.IO
import com.togger.common.PageRequest
import com.togger.common.PaginatedResponse
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.dto._
import com.togger.preview._

import java.util.UUID

class MilestoneTemplateFeServicePreview(
  ctx: PreviewContext,
  data: MilestoneTemplatesPreviewData,
  stepTemplates: MilestoneStepTemplatesPreviewData
) extends MilestoneTemplateFeService {

  private val NotFoundMessage = "Milestone template not found"

  private def notInProject(stepTemplateId: UUID) =
    s"Milestone step template $stepTemplateId does not belong to the project"

  override def createMilestoneTemplate(dto: CreateMilestoneTemplateReq): TgRespIO[FindMilestoneTemplateResp] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer(
        stepTemplateError(dto.projectId, dto.stages.flatten).fold {
          val template = data.templates.upsert(
            PreviewMilestoneTemplate(UUID.randomUUID(), dto.projectId, ctx.clock.currentInstant)
          )
          val _ = data.templateVersions.upsert(newVersion(template.id, dto.name, dto.description, dto.stages))
          PreviewResponses.ok(toDto(template))
        }(PreviewResponses.badRequest[FindMilestoneTemplateResp](_))
      )
    )

  override def updateMilestoneTemplate(dto: UpdateMilestoneTemplateReq): TgRespIO[FindMilestoneTemplateResp] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer(data.templates.find(dto.id) match {
        case None => PreviewResponses.badRequest[FindMilestoneTemplateResp](NotFoundMessage)
        case Some(template) =>
          stepTemplateError(template.projectId, dto.stages.flatten).fold {
            val unchanged = data
              .latestVersion(template.id)
              .exists(v => v.name == dto.name && v.description == dto.description && sameStages(v.stages, dto.stages))
            if (!unchanged) {
              val _ =
                data.templateVersions.upsert(newVersion(template.id, dto.name, dto.description, dto.stages))
            }
            PreviewResponses.ok(toDto(template))
          }(PreviewResponses.badRequest[FindMilestoneTemplateResp](_))
      })
    )

  override def findByProjectId(
    projectId: UUID,
    page: PageRequest
  ): TgRespIO[PaginatedResponse[FindMilestoneTemplateResp]] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer {
        val found = data.templates
          .filter(_.projectId == projectId)
          .flatMap(toDtoOpt)
          .sortBy(template => (template.name, template.id.toString))
        PreviewResponses.paged(found, page)
      }
    )

  override def findMilestoneTemplateById(id: UUID): TgRespIO[FindMilestoneTemplateResp] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer(
        data.templates
          .find(id)
          .flatMap(toDtoOpt)
          .fold(PreviewResponses.badRequest[FindMilestoneTemplateResp](NotFoundMessage))(PreviewResponses.ok(_))
      )
    )

  override def listVersions(id: UUID, page: PageRequest): TgRespIO[PaginatedResponse[MilestoneTemplateVersionDto]] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer(
        PreviewResponses.paged(
          data.templateVersions.filter(_.milestoneTemplateId == id).reverse.map(toVersionDto),
          page
        )
      )
    )

  override def deleteMilestoneTemplateById(id: UUID): TgRespIO[TgEmptyBody] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer {
        if (data.templates.find(id).isEmpty) PreviewResponses.badRequest[TgEmptyBody](NotFoundMessage)
        else {
          val _ = data.deleteTemplate(id)
          PreviewResponses.okEmpty("Milestone template deleted successfully")
        }
      }
    )

  /** Every step template has to exist and belong to the project, like in the backend. */
  private def stepTemplateError(projectId: UUID, stepTemplateIds: List[UUID]): Option[String] =
    stepTemplateIds.distinct
      .find(id => !stepTemplates.templates.find(id).exists(_.projectId == projectId))
      .map(notInProject)

  /** The same step templates in the same sequence of stages; the order inside a stage does not matter. */
  private def sameStages(left: List[List[UUID]], right: List[List[UUID]]): Boolean =
    left.map(_.toSet).filter(_.nonEmpty) == right.map(_.toSet).filter(_.nonEmpty)

  private def newVersion(
    templateId: UUID,
    name: String,
    description: String,
    stages: List[List[UUID]]
  ): PreviewMilestoneTemplateVersion =
    PreviewMilestoneTemplateVersion(
      UUID.randomUUID(),
      templateId,
      name,
      description,
      stages.map(_.distinct).filter(_.nonEmpty),
      ctx.clock.currentInstant
    )

  private def stepsOf(stages: List[List[UUID]]): List[MilestoneTemplateStepDto] =
    stages.zipWithIndex.flatMap { case (ids, stage) =>
      ids
        .flatMap(id => stepTemplates.latestVersion(id).map(id -> _))
        .sortBy { case (id, version) => (version.name, id.toString) }
        .map { case (id, version) => MilestoneTemplateStepDto(id, version.name, version.description, stage) }
    }

  private def toVersionDto(version: PreviewMilestoneTemplateVersion): MilestoneTemplateVersionDto =
    MilestoneTemplateVersionDto(
      version.version,
      version.milestoneTemplateId,
      version.name,
      version.description,
      stepsOf(version.stages),
      version.createdAt
    )

  private def toDtoOpt(template: PreviewMilestoneTemplate): Option[FindMilestoneTemplateResp] =
    data.latestVersion(template.id).map { version =>
      FindMilestoneTemplateResp(
        id = template.id,
        projectId = template.projectId,
        name = version.name,
        description = version.description,
        steps = stepsOf(version.stages),
        latestVersion = version.version,
        updatedAt = version.createdAt
      )
    }

  private def toDto(template: PreviewMilestoneTemplate): FindMilestoneTemplateResp = toDtoOpt(template).get
}
