package com.togger.ui.milestone

import com.raquo.laminar.api.L._
import com.togger.dto.FindMilestoneStepResp
import com.togger.ui.components.TgProgressColor
import com.togger.ui.components.TgProgressStyles
import com.togger.ui.milestonetemplate.MilestoneStageEditing
import com.togger.ui.milestonetemplate.MilestoneStageEditing.Stages
import org.scalajs.dom

import java.util.UUID

/** What the graph can change when it is editable: `onLayoutChange` gets the new stages of step ids (the steps of a
  * stage run in parallel) and `onAdd(parallel)` asks for a new step, after the last stage or in parallel with it.
  * Keeping the layout and creating the step are up to the caller.
  */
case class MilestoneStepsGraphEditing(onLayoutChange: Stages[UUID] => Unit, onAdd: Boolean => Unit)

/** The steps of a milestone as a graph of progress bars: the stages side by side in sequence and the steps of a stage,
  * which run in parallel, stacked in one column. With `editing` a step can be dragged onto another stage to run in
  * parallel with it, or into the gap between stages to get a stage of its own, and steps can be added; without it the
  * graph is read only. Callers pass only the steps to show (no default step).
  */
class MilestoneStepsGraphComponent(
  steps: Signal[Seq[FindMilestoneStepResp]],
  editing: Option[MilestoneStepsGraphEditing] = None
) {

  /** Where a dragged step would land: in the stage, or in a stage of its own in the gap before a stage (the number of
    * stages is the gap after the last).
    */
  private sealed trait DropTarget
  private case class IntoStage(stage: Int) extends DropTarget
  private case class IntoGap(insertion: Int) extends DropTarget

  private val dragged = Var(Option.empty[UUID])
  private val dropTarget = Var(Option.empty[DropTarget])

  private val stagesSignal: Signal[Stages[FindMilestoneStepResp]] =
    steps.map(
      _.groupBy(_.position).toList.sortBy { case (position, _) => position }.map { case (_, stage) => stage.toList }
    )

  /** Written out in full so Tailwind finds the classes. */
  private def fillCls(color: TgProgressColor): String = color match {
    case TgProgressColor.Success => "bg-success-soft"
    case TgProgressColor.Dark    => "bg-dark-soft"
    case _                       => "bg-brand-soft"
  }

  private def clearDrag(): Unit = {
    dragged.set(None)
    dropTarget.set(None)
  }

  private def idStages(stages: Stages[FindMilestoneStepResp]): Stages[UUID] = stages.map(_.map(_.id))

  private def drop(stages: Stages[FindMilestoneStepResp]): Unit = {
    val ids = idStages(stages)
    for {
      change <- editing
      step <- dragged.now()
      target <- dropTarget.now()
    } {
      val edited = target match {
        case IntoStage(stage)   => MilestoneStageEditing.joinStage(ids, step, stage)
        case IntoGap(insertion) => MilestoneStageEditing.moveToNewStage(ids, step, insertion)
      }
      if (edited != ids) change.onLayoutChange(edited)
    }
    clearDrag()
  }

  private def dragModifiers(step: FindMilestoneStepResp): Seq[Modifier[HtmlElement]] =
    Seq(
      draggable(true),
      cls("cursor-grab"),
      onDragStart --> { event =>
        event.dataTransfer.setData("text/plain", step.id.toString)
        event.dataTransfer.effectAllowed = dom.DataTransferEffectAllowedKind.move
        dragged.set(Some(step.id))
      },
      onDragEnd --> (_ => clearDrag()),
      cls <-- dragged.signal.map(id => if (id.contains(step.id)) "opacity-50" else "")
    )

  private def stepBar(step: FindMilestoneStepResp): HtmlElement = {
    val bar = StepBar.of(step)
    val percent = TgProgressStyles.percent(bar.value, bar.max)
    div(
      cls("min-w-0"),
      title := bar.title,
      editing.fold[Modifier[HtmlElement]](emptyMod)(_ => dragModifiers(step)),
      div(cls("mb-1 truncate text-center text-sm font-medium text-heading"), s"${step.status.emoji} ${step.name}"),
      div(
        cls("relative h-8 overflow-hidden rounded-base border border-default-medium bg-neutral-primary-soft"),
        div(cls(s"absolute inset-y-0 left-0 ${fillCls(bar.color)}"), width(s"$percent%")),
        div(
          cls("relative flex h-full items-center justify-center text-sm text-heading"),
          s"${step.tasksTotal}/${step.tasksDone} ($percent%)"
        )
      )
    )
  }

  private def hovering(target: DropTarget): Signal[Boolean] =
    dragged.signal.combineWith(dropTarget.signal).map { case (drag, over) => drag.isDefined && over.contains(target) }

  /** A stage with several steps gets a tinted box to make the parallel run obvious. While dragging, every stage takes a
    * drop that makes the step run in parallel with it.
    */
  private def stageColumn(
    stage: Int,
    inStage: List[FindMilestoneStepResp],
    stages: Stages[FindMilestoneStepResp]
  ): HtmlElement =
    div(
      cls("flex min-w-0 flex-1 flex-col gap-3 p-2"),
      if (inStage.size > 1) cls("rounded-base border border-default-medium bg-neutral-secondary-soft")
      else cls("border border-transparent"),
      editing.fold[Modifier[HtmlElement]](emptyMod) { _ =>
        Seq[Modifier[HtmlElement]](
          onDragOver.preventDefault --> (_ => if (dragged.now().isDefined) dropTarget.set(Some(IntoStage(stage)))),
          onDrop.preventDefault --> (_ => drop(stages)),
          cls("rounded-base ring-2 ring-blue-500") <-- hovering(IntoStage(stage))
        )
      },
      inStage.map(stepBar)
    )

  /** The drop zone between two stages; a step dropped here gets a stage of its own. */
  private def gap(insertion: Int, stages: Stages[FindMilestoneStepResp]): HtmlElement =
    div(
      cls("h-3 w-full shrink-0 rounded-base sm:h-auto sm:w-3"),
      onDragOver.preventDefault --> (_ => if (dragged.now().isDefined) dropTarget.set(Some(IntoGap(insertion)))),
      onDrop.preventDefault --> (_ => drop(stages)),
      cls("bg-blue-500") <-- hovering(IntoGap(insertion)),
      cls("bg-neutral-secondary-medium") <-- dragged.signal.combineWith(hovering(IntoGap(insertion))).map {
        case (drag, over) => drag.isDefined && !over
      }
    )

  private def graph(stages: Stages[FindMilestoneStepResp]): HtmlElement = {
    val columns = stages.zipWithIndex.map { case (inStage, stage) => stageColumn(stage, inStage, stages) }
    div(
      cls("flex flex-col sm:flex-row"),
      if (editing.isEmpty) cls("gap-4")
      else cls("gap-0"),
      if (editing.isEmpty) columns
      else
        columns.zipWithIndex.flatMap { case (column, index) => List(gap(index, stages), column) } :+
          gap(stages.size, stages)
    )
  }

  private def addButton(label: String, tip: String, parallel: Boolean, onAdd: Boolean => Unit): HtmlElement =
    button(
      typ("button"),
      cls(
        "px-3 py-1.5 text-sm font-medium text-heading bg-neutral-secondary-medium border border-default-medium rounded-base hover:bg-neutral-tertiary-medium"
      ),
      title := tip,
      label,
      onClick.mapToUnit --> (_ => onAdd(parallel))
    )

  private val addButtons: Modifier[HtmlElement] =
    editing.fold[Modifier[HtmlElement]](emptyMod) { change =>
      div(
        cls("flex flex-wrap items-center gap-2 mb-3"),
        addButton("+ Sequential step", "Add a step after the last stage", parallel = false, change.onAdd),
        addButton(
          "+ Parallel step",
          "Add a step that runs in parallel with the last stage",
          parallel = true,
          change.onAdd
        ),
        span(
          cls("text-sm text-body"),
          "Drag a step onto a stage to run in parallel with it, or between stages to run in sequence."
        )
      )
    }

  val element: HtmlElement =
    div(
      cls("mb-4"),
      addButtons,
      child <-- stagesSignal.map(stages =>
        if (stages.isEmpty) editing.fold[HtmlElement](div())(_ => div(cls("text-sm text-body"), "No steps yet."))
        else graph(stages)
      )
    )
}
