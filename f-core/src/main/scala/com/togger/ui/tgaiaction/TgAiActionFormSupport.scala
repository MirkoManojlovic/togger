package com.togger.ui.tgaiaction

import com.togger.common.TgResponseTypes.TgResp
import com.togger.dto.{CreateTgAiActionReq, FindTgAiActionResp, TgAiEndpointDto, TgAiModelDto, UpdateTgAiActionReq}

import java.util.UUID

/** `tgPromptTemplateId` is the id as text, empty for no prompt template, because the select works on text. */
case class TgAiActionForm(
  id: UUID = UUID.randomUUID(),
  name: String = "",
  prompt: String = "",
  tgPromptTemplateId: String = "",
  model: String = "",
  endpointIds: Set[Int] = Set.empty
)

object TgAiActionFormSupport {

  val promptHint: String = "Write the prompt of the action here"

  def fromDto(dto: FindTgAiActionResp): TgAiActionForm =
    TgAiActionForm(
      id = dto.id,
      name = dto.name,
      prompt = dto.prompt,
      tgPromptTemplateId = dto.tgPromptTemplateId.map(_.toString).getOrElse(""),
      model = dto.model,
      endpointIds = dto.endpointIds.toSet
    )

  def toCreateReq(form: TgAiActionForm): CreateTgAiActionReq =
    CreateTgAiActionReq(form.name, form.prompt, templateId(form), form.model, sortedEndpointIds(form))

  def toUpdateReq(form: TgAiActionForm): UpdateTgAiActionReq =
    UpdateTgAiActionReq(form.id, form.name, form.prompt, templateId(form), form.model, sortedEndpointIds(form))

  def toggleEndpoint(form: TgAiActionForm, endpointId: Int, selected: Boolean): TgAiActionForm =
    form.copy(endpointIds = if (selected) form.endpointIds + endpointId else form.endpointIds - endpointId)

  /** The names of the endpoints the action is bound to, in endpoint order. */
  def endpointNames(endpointIds: List[Int], endpoints: Seq[TgAiEndpointDto]): String =
    endpoints.filter(endpoint => endpointIds.contains(endpoint.id)).map(_.name).mkString(", ")

  /** The form stays usable when the models cannot be listed, e.g. the local Ollama is not running. */
  def modelsOrEmpty(result: TgResp[List[TgAiModelDto]]): Seq[TgAiModelDto] =
    result.toOption.flatMap(_.body).getOrElse(Seq.empty)

  /** The model ids to pick from; the current model stays selectable when the server no longer lists it. */
  def modelIds(models: Seq[TgAiModelDto], currentModel: String): Seq[String] = {
    val listed = models.map(_.id)
    if (currentModel.isEmpty || listed.contains(currentModel)) listed else currentModel +: listed
  }

  private def templateId(form: TgAiActionForm): Option[UUID] =
    Option(form.tgPromptTemplateId).filter(_.nonEmpty).map(UUID.fromString)

  private def sortedEndpointIds(form: TgAiActionForm): List[Int] = form.endpointIds.toList.sorted
}
