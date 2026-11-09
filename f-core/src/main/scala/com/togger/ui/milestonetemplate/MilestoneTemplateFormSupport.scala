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
  stages: List[List[UUID]] = Nil
)

object MilestoneTemplateFormSupport {

  val descriptionHint: String = "When to use this template"

  private val NoStepValue = ""

  private val DeletedStepName = "(deleted step template)"

  /** The step templates of the form stage by stage, each with its name; one that no longer exists keeps its place. */
  def stepNames(stages: List[List[UUID]], available: Seq[FindMilestoneStepTemplateResp]): List[List[String]] = {
    val namesById = available.map(template => template.id -> template.name).toMap
    stages.map(_.map(namesById.getOrElse(_, DeletedStepName)))
  }

  /** The steps by stage, numbered 1, 2a, 2b, ... (the steps of a stage run in parallel), with buttons to move them, to
    * run them in parallel with the previous stage or after the others of their stage, and to remove them; and a
    * dropdown to add a step template in a stage of its own at the end.
    */
  def stepsEditor(
    form: Var[MilestoneTemplateForm],
    available: Signal[Seq[FindMilestoneStepTemplateResp]]
  ): HtmlDiv =
    div(
      label(cls("block mb-2.5 text-sm font-medium text-heading"), "Steps"),
      child <-- form.signal
        .map(_.stages)
        .combineWith(available)
        .map { case (stages, templates) =>
          if (stages.isEmpty) p(cls("mb-3 text-sm text-body-subtle"), "No steps yet")
          else
            div(
              cls("mb-3"),
              MilestoneStageEditing.rows(stepNames(stages, templates)).map { at =>
                div(
                  cls("flex items-center justify-between mb-1 p-2 border border-default rounded-base shadow-md"),
                  span(cls("text-sm text-heading"), s"${at.label}. ${at.step}"),
                  div(
                    iconButton(
                      "Move up",
                      span("↑"),
                      rowIconButtonCls,
                      rowButtonMarginCls,
                      onClick.mapToUnit --> (_ =>
                        edit(form, MilestoneStageEditing.move(_, at.stage, at.indexInStage, -1))
                      )
                    ),
                    iconButton(
                      "Move down",
                      span("↓"),
                      rowIconButtonCls,
                      rowButtonMarginCls,
                      onClick.mapToUnit --> (_ =>
                        edit(form, MilestoneStageEditing.move(_, at.stage, at.indexInStage, 1))
                      )
                    ),
                    if (at.stage > 0)
                      iconButton(
                        "Run in parallel with the previous stage",
                        span("∥"),
                        rowIconButtonCls,
                        rowButtonMarginCls,
                        onClick.mapToUnit --> (_ =>
                          edit(form, MilestoneStageEditing.joinPreviousStage(_, at.stage, at.indexInStage))
                        )
                      )
                    else emptyNode,
                    if (stages(at.stage).size > 1)
                      iconButton(
                        "Run after the others of its stage",
                        span("→"),
                        rowIconButtonCls,
                        rowButtonMarginCls,
                        onClick.mapToUnit --> (_ =>
                          edit(form, MilestoneStageEditing.splitFromStage(_, at.stage, at.indexInStage))
                        )
                      )
                    else emptyNode,
                    iconButton(
                      "Remove",
                      TgIcons.trashIcon(className = "w-4 h-4"),
                      rowIconButtonDangerCls,
                      rowButtonMarginCls,
                      onClick.mapToUnit --> (_ =>
                        edit(form, MilestoneStageEditing.remove(_, at.stage, at.indexInStage))
                      )
                    )
                  )
                )
              }
            )
        },
      child <-- available.map(templates => addStepDropdown(form, templates))
    )

  private def edit(
    form: Var[MilestoneTemplateForm],
    change: MilestoneStageEditing.Stages[UUID] => MilestoneStageEditing.Stages[UUID]
  ): Unit =
    form.update(f => f.copy(stages = change(f.stages)))

  private def addStepDropdown(form: Var[MilestoneTemplateForm], templates: Seq[FindMilestoneStepTemplateResp]) =
    TgSelectDropdown(
      inputSignal = Val(NoStepValue),
      inputStateUpdater = Observer[String] { value =>
        if (value != NoStepValue)
          edit(form, MilestoneStageEditing.add(_, UUID.fromString(value)))
      },
      labelValue = "Add a step",
      options = templates.sortBy(_.name).map { template =>
        TgSelectDropdownOption(template.id.toString, DropdownMeta(template.name, ""), () => span(template.name))
      },
      placeholder = "Select a step template to add"
    ).htmlElement
}
