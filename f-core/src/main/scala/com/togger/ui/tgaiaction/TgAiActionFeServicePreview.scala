package com.togger.ui.tgaiaction

import cats.effect.IO
import com.togger.ai.TgAiEndpoints
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.{PageRequest, PaginatedResponse}
import com.togger.dto._
import com.togger.preview._

import java.util.UUID

class TgAiActionFeServicePreview(ctx: PreviewContext, projects: ProjectsPreviewData, data: AiPreviewData)
    extends TgAiActionFeService {

  private val NotFoundMessage = "AI action not found"
  private val DuplicateNameMessage = "An AI action with this name already exists"
  private val UnknownEndpointMessage = "Unknown AI endpoint"
  private val UnknownPromptTemplateMessage = "Prompt template not found"
  private val NotBoundMessage = "The AI action is not bound to this AI endpoint"
  private val NotAMemberMessage = "You are not a member of this project"

  override def create(dto: CreateTgAiActionReq): TgRespIO[FindTgAiActionResp] =
    PreviewResponses.authenticated(ctx.loginRepo)(callerId =>
      IO.defer(
        validate(callerId, dto.name, dto.tgPromptTemplateId, dto.endpointIds, None).fold {
          val now = ctx.clock.currentInstant
          val action = PreviewAiAction(
            UUID.randomUUID(),
            callerId,
            dto.name,
            dto.prompt,
            dto.tgPromptTemplateId,
            dto.model,
            dto.endpointIds.distinct.sorted,
            now,
            now
          )
          PreviewResponses.ok(toDto(data.actions.upsert(action)))
        }(PreviewResponses.badRequest[FindTgAiActionResp](_))
      )
    )

  override def update(dto: UpdateTgAiActionReq): TgRespIO[FindTgAiActionResp] =
    owned(dto.id) { (callerId, action) =>
      validate(callerId, dto.name, dto.tgPromptTemplateId, dto.endpointIds, Some(action.id)).fold {
        val endpointIds = dto.endpointIds.distinct.sorted
        val updated = data.actions.upsert(
          action.copy(
            name = dto.name,
            prompt = dto.prompt,
            tgPromptTemplateId = dto.tgPromptTemplateId,
            model = dto.model,
            endpointIds = endpointIds,
            updatedAt = ctx.clock.currentInstant
          )
        )
        data.defaults.deleteWhere(d => d.actionId == action.id && !endpointIds.contains(d.endpointId))
        PreviewResponses.ok(toDto(updated))
      }(PreviewResponses.badRequest[FindTgAiActionResp](_))
    }

  override def findAll(page: PageRequest): TgRespIO[PaginatedResponse[FindTgAiActionResp]] =
    PreviewResponses.authenticated(ctx.loginRepo)(callerId =>
      IO.defer {
        val actions = data.actions
          .filter(_.createdBy == callerId)
          .sortBy(action => (action.createdAt.toEpochMilli, action.id.toString))
          .reverse
        PreviewResponses.paged(actions.map(toDto), page)
      }
    )

  override def findById(id: UUID): TgRespIO[FindTgAiActionResp] =
    owned(id)((_, action) => PreviewResponses.ok(toDto(action)))

  override def deleteById(id: UUID): TgRespIO[TgEmptyBody] =
    owned(id) { (_, action) =>
      data.deleteAction(action.id)
      PreviewResponses.okEmpty("AI action deleted successfully")
    }

  override def listEndpoints: TgRespIO[List[TgAiEndpointDto]] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ => PreviewResponses.ok(data.endpoints))

  override def listModels: TgRespIO[List[TgAiModelDto]] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ => PreviewResponses.ok(data.models))

  override def findDefaults(projectId: UUID): TgRespIO[List[FindTgAiActionDefaultResp]] =
    PreviewResponses.authenticated(ctx.loginRepo)(callerId =>
      IO.defer(
        PreviewResponses.ok(
          data.defaults
            .filter(d => d.accountId == callerId && d.projectId == projectId)
            .sortBy(_.endpointId)
            .map(toDefaultDto)
        )
      )
    )

  override def setDefault(dto: SetTgAiActionDefaultReq): TgRespIO[FindTgAiActionDefaultResp] =
    PreviewResponses.authenticated(ctx.loginRepo)(callerId =>
      IO.defer {
        val action = data.actions.find(dto.actionId).filter(_.createdBy == callerId)
        if (!projects.isMember(dto.projectId, callerId)) PreviewResponses.badRequest[FindTgAiActionDefaultResp](NotAMemberMessage)
        else if (action.isEmpty) PreviewResponses.badRequest[FindTgAiActionDefaultResp](NotFoundMessage)
        else if (!action.exists(_.endpointIds.contains(dto.endpointId)))
          PreviewResponses.badRequest[FindTgAiActionDefaultResp](NotBoundMessage)
        else {
          val existing = data.defaults.all.find(d =>
            d.accountId == callerId && d.projectId == dto.projectId && d.endpointId == dto.endpointId
          )
          val saved = data.defaults.upsert(
            PreviewAiActionDefault(
              existing.fold(UUID.randomUUID())(_.id),
              callerId,
              dto.projectId,
              dto.endpointId,
              dto.actionId
            )
          )
          PreviewResponses.ok(toDefaultDto(saved))
        }
      }
    )

  override def clearDefault(projectId: UUID, endpointId: Int): TgRespIO[TgEmptyBody] =
    PreviewResponses.authenticated(ctx.loginRepo)(callerId =>
      IO.defer {
        data.defaults.deleteWhere(d => d.accountId == callerId && d.projectId == projectId && d.endpointId == endpointId)
        PreviewResponses.okEmpty("AI action default cleared successfully")
      }
    )

  private def owned[A](id: UUID)(f: (UUID, PreviewAiAction) => TgRespIO[A]): TgRespIO[A] =
    PreviewResponses.authenticated(ctx.loginRepo)(callerId =>
      IO.defer(
        data.actions.find(id).filter(_.createdBy == callerId).fold(PreviewResponses.badRequest[A](NotFoundMessage))(
          f(callerId, _)
        )
      )
    )

  private def validate(
    callerId: UUID,
    name: String,
    promptTemplateId: Option[UUID],
    endpointIds: List[Int],
    exceptId: Option[UUID]
  ): Option[String] =
    if (endpointIds.exists(id => TgAiEndpoints.withValueOpt(id).isEmpty)) Some(UnknownEndpointMessage)
    else if (data.actions.all.exists(a => a.createdBy == callerId && a.name == name && !exceptId.contains(a.id)))
      Some(DuplicateNameMessage)
    else if (!promptTemplateId.forall(id => data.promptTemplates.find(id).exists(_.createdBy == callerId)))
      Some(UnknownPromptTemplateMessage)
    else None

  private def toDto(action: PreviewAiAction): FindTgAiActionResp =
    FindTgAiActionResp(
      action.id,
      action.name,
      action.prompt,
      action.tgPromptTemplateId,
      action.model,
      action.endpointIds,
      action.createdAt,
      action.updatedAt
    )

  private def toDefaultDto(default: PreviewAiActionDefault): FindTgAiActionDefaultResp =
    FindTgAiActionDefaultResp(default.projectId, default.endpointId, default.actionId)
}
