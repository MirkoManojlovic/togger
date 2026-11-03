package com.togger.ui.milestonetemplate

import com.raquo.laminar.api.L._
import com.togger.dto.FindMilestoneStepTemplateResp
import com.togger.ui.auth.TgHtmlTypes.HtmlDiv
import com.togger.ui.components.DropdownMeta
import com.togger.ui.components.TgSelectDropdown
import com.togger.ui.components.TgSelectDropdownOption
import com.togger.ui.organization.TgIcons
import com.togger.ui.task.TaskConfigTableSupport.iconButton
import com.togger.ui.task.TaskConfigTableSupport.rowButtonMarginCls
import com.togger.ui.task.TaskConfigTableSupport.rowIconButtonCls
import com.togger.ui.task.TaskConfigTableSupport.rowIconButtonDangerCls

import java.util.UUID

case class MilestoneTemplateForm(
  projectId: UUID = UUID.randomUUID(),
  id: UUID = UUID.randomUUID(),
  name: String = "",
  description: String = "",
  stepTemplateIds: List[UUID] = Nil
)

object MilestoneTemplateFormSupport {

  val descriptionHint: String = "When to use this template"

  private val NoStepValue = ""

  private val DeletedStepName = "(deleted step template)"

  /** The step templates of the form in order, each with its name; one that no longer exists keeps its place. */
  def stepNames(stepTemplateIds: List[UUID], available: Seq[FindMilestoneStepTemplateResp]): List[String] = {
    val namesById = available.map(template => template.id -> template.name).toMap
    stepTemplateIds.map(namesById.getOrElse(_, DeletedStepName))
  }

  /** The ordered steps with buttons to move and remove them, and a dropdown to add a step template at the end. */
  def stepsEditor(
    form: Var[MilestoneTemplateForm],
    available: Signal[Seq[FindMilestoneStepTemplateResp]]
  ): HtmlDiv =
    div(
      label(cls("block mb-2.5 text-sm font-medium text-heading"), "Steps"),
      child <-- form.signal
        .map(_.stepTemplateIds)
        .combineWith(available)
        .map {
          case (ids, templates) =>
            if (ids.isEmpty) p(cls("mb-3 text-sm text-body-subtle"), "No steps yet")
            else
              div(
                cls("mb-3"),
                stepNames(ids, templates).zipWithIndex.map { case (name, index) =>
                  div(
                    cls("flex items-center justify-between mb-1 p-2 border border-default rounded-base"),
                    span(cls("text-sm text-heading"), s"${index + 1}. $name"),
                    div(
                      iconButton(
                        "Move up",
                        span("↑"),
                        rowIconButtonCls,
                        rowButtonMarginCls,
                        onClick.mapToUnit --> (_ => moveStep(form, index, -1))
                      ),
                      iconButton(
                        "Move down",
                        span("↓"),
                        rowIconButtonCls,
                        rowButtonMarginCls,
                        onClick.mapToUnit --> (_ => moveStep(form, index, 1))
                      ),
                      iconButton(
                        "Remove",
                        TgIcons.trashIcon(className = "w-4 h-4"),
                        rowIconButtonDangerCls,
                        rowButtonMarginCls,
                        onClick.mapToUnit --> (_ =>
                          form.update(f =>
                            f.copy(stepTemplateIds = MilestoneTemplateStepsEditing.remove(f.stepTemplateIds, index))
                          )
                        )
                      )
                    )
                  )
                }
              )
        },
      child <-- available.map(templates => addStepDropdown(form, templates))
    )

  private def moveStep(form: Var[MilestoneTemplateForm], index: Int, delta: Int): Unit =
    form.update(f => f.copy(stepTemplateIds = MilestoneTemplateStepsEditing.move(f.stepTemplateIds, index, delta)))

  private def addStepDropdown(form: Var[MilestoneTemplateForm], templates: Seq[FindMilestoneStepTemplateResp]) =
    TgSelectDropdown(
      inputSignal = Val(NoStepValue),
      inputStateUpdater = Observer[String] { value =>
        if (value != NoStepValue)
          form.update(f =>
            f.copy(stepTemplateIds = MilestoneTemplateStepsEditing.add(f.stepTemplateIds, UUID.fromString(value)))
          )
      },
      labelValue = "Add a step",
      options = templates.sortBy(_.name).map { template =>
        TgSelectDropdownOption(template.id.toString, DropdownMeta(template.name, ""), () => span(template.name))
      },
      placeholder = "Select a step template to add"
    ).htmlElement
}
