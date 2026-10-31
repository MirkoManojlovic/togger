package com.togger.preview

import com.togger.ai.TgAiEndpoints
import com.togger.dto.{TgAiEndpointDto, TgAiModelDto, TgPromptTemplateVersionDto}
import io.circe.generic.auto._

import java.time.Instant
import java.util.UUID

case class PreviewPromptTemplate(id: UUID, createdBy: UUID, createdAt: Instant)

case class PreviewAiAction(
  id: UUID,
  createdBy: UUID,
  name: String,
  prompt: String,
  tgPromptTemplateId: Option[UUID],
  model: String,
  endpointIds: List[Int],
  createdAt: Instant,
  updatedAt: Instant
)

/** The default action of an account for one endpoint within one project. */
case class PreviewAiActionDefault(id: UUID, accountId: UUID, projectId: UUID, endpointId: Int, actionId: UUID)

/** Prompt templates (with their versions), AI actions and the per account/project/endpoint default actions. */
class AiPreviewData(db: PreviewDb, projects: ProjectsPreviewData) {

  val promptTemplates: PreviewCollection[PreviewPromptTemplate] =
    db.collection[PreviewPromptTemplate](AiPreviewData.PromptTemplatesName, _.id)

  /** Versions are stored oldest first, the last one of a prompt is its current content. */
  val promptVersions: PreviewCollection[TgPromptTemplateVersionDto] =
    db.collection[TgPromptTemplateVersionDto](AiPreviewData.PromptVersionsName, _.version)

  val actions: PreviewCollection[PreviewAiAction] = db.collection[PreviewAiAction](AiPreviewData.ActionsName, _.id)

  val defaults: PreviewCollection[PreviewAiActionDefault] =
    db.collection[PreviewAiActionDefault](AiPreviewData.DefaultsName, _.id)

  val models: List[TgAiModelDto] = AiPreviewData.ModelIds.map(TgAiModelDto(_))

  val endpoints: List[TgAiEndpointDto] =
    TgAiEndpoints.values.map(endpoint => TgAiEndpointDto(endpoint.value, endpoint.endpointName)).toList

  projects.onProjectDeleted(projectId => defaults.deleteWhere(_.projectId == projectId))

  def latestVersion(promptId: UUID): Option[TgPromptTemplateVersionDto] =
    promptVersions.filter(_.tgPromptTemplateId == promptId).lastOption

  def deletePromptTemplate(id: UUID): Boolean = {
    actions.all.filter(_.tgPromptTemplateId.contains(id)).foreach(action => actions.upsert(action.copy(tgPromptTemplateId = None)))
    promptVersions.deleteWhere(_.tgPromptTemplateId == id)
    promptTemplates.delete(id)
  }

  def deleteAction(id: UUID): Boolean = {
    defaults.deleteWhere(_.actionId == id)
    actions.delete(id)
  }
}

object AiPreviewData {
  val PromptTemplatesName = "tg-prompt-templates"
  val PromptVersionsName = "tg-prompt-template-versions"
  val ActionsName = "tg-ai-actions"
  val DefaultsName = "tg-ai-action-defaults"

  val ModelIds: List[String] = List("gpt-4.1", "gpt-4.1-mini", "gpt-4.1-nano", "gpt-4o", "gpt-4o-mini", "o4-mini")
}
