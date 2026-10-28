package com.togger.ui.tgaiaction

import com.raquo.laminar.api.L._
import com.togger.dto.{FindTgPromptTemplateResp, TgAiEndpointDto, TgAiModelDto}
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.TgSelect.TgSelectSyntax
import com.togger.ui.auth.{TgCheckbox, TgInputType, TgSelect}
import com.togger.ui.components.wysiwyg.WYSIWYGTextEditorSplit.WYSIWYGTextEditorSplitSyntax
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro

import scala.collection.immutable.ListMap

/** The fields shared by the create and the edit form. The prompt templates, the models and the endpoints are read before
  * the form is shown, because the selects and the checkboxes are built from them.
  */
class TgAiActionFormFields(
  formState: Var[TgAiActionForm],
  promptTemplates: Seq[FindTgPromptTemplateResp],
  models: Seq[TgAiModelDto],
  endpoints: Seq[TgAiEndpointDto]
) {

  private val nameInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.name),
    errors = EventStream.empty,
    labelValue = "Name",
    tgInputType = TgInputType.Text
  )

  private val modelSelect = formState.tgSelectManualError(
    inputAndUpdater = selectorAndUpdaterMacro(_.model),
    labelValue = "Model",
    emptyValue = Some("Select a model"),
    valuesAndDisplays = ListMap(
      TgAiActionFormSupport.modelIds(models, formState.now().model).map(id => id -> id): _*
    )
  )

  private val promptEditor = formState.wysiwygSplitEditorSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.prompt),
    labelValue = "Prompt",
    placeholderValue = TgAiActionFormSupport.promptHint
  )

  private val promptTemplateSelect = TgSelect(
    inputSignal = formState.signal.map(_.tgPromptTemplateId),
    inputStateUpdater = formState.updater[String]((form, id) => form.copy(tgPromptTemplateId = id)),
    labelValue = "Prompt template",
    emptyValue = Some("No prompt template"),
    valuesAndDisplays = ListMap(promptTemplates.map(template => template.id.toString -> template.title): _*)
  )

  private def endpointCheckbox(endpoint: TgAiEndpointDto): HtmlElement =
    TgCheckbox(
      inputSignal = formState.signal.map(_.endpointIds.contains(endpoint.id)),
      inputStateUpdater = formState.updater[Boolean]((form, selected) =>
        TgAiActionFormSupport.toggleEndpoint(form, endpoint.id, selected)
      ),
      labelValue = endpoint.name
    ).htmlElement

  val elements: Seq[HtmlElement] = Seq(
    nameInput.htmlElement.amend(cls("mb-4")),
    modelSelect.htmlElement.amend(cls("mb-4")),
    promptTemplateSelect.htmlElement.amend(cls("mb-4")),
    promptEditor.htmlElement.amend(cls("mb-4")),
    div(
      cls("mb-4"),
      label(cls("block mb-2.5 text-sm font-medium text-heading"), "AI endpoints"),
      endpoints.map(endpointCheckbox)
    )
  )
}
