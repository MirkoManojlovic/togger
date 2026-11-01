package com.togger.ui.milestone

import com.raquo.laminar.api.L._
import com.togger.common.TgClock
import com.togger.milestone.MilestoneStepStatus
import com.togger.ui.auth.TgCard
import com.togger.ui.components.{ChartColorBadgeColor, TgBadge}
import com.togger.ui.task.TgDateFormater
import com.togger.ui.timebox.gantt.{DiffColors, GanttAxis, GanttBarDrag, GanttBarProgress, GanttChartLayout, GanttColors}

import java.time.{DayOfWeek, LocalDate}

/** A step as the Gantt chart draws it. */
case class MilestoneGanttStep(
  key: String,
  name: String,
  startDate: LocalDate,
  dueDate: LocalDate,
  status: MilestoneStepStatus = MilestoneStepStatus.Planned,
  tasksTotal: Int = 0,
  tasksDone: Int = 0
)

/** The steps of a milestone as a Gantt chart that looks like the one of a timebox: one column per work day of the
  * milestone, one row per step, each step as a bar from its start date to its due date. With `onDatesChange` the bars
  * can be dragged to move a step or resize it; the chart only reports the new dates and leaves keeping them to the
  * caller. Without it the chart is read only.
  */
class MilestoneStepsGanttComponent(
  steps: Signal[Seq[MilestoneGanttStep]],
  milestoneRange: Signal[Option[(LocalDate, LocalDate)]],
  workDays: Signal[Set[DayOfWeek]],
  tgDateFormater: TgDateFormater,
  tgClock: TgClock,
  onDatesChange: Option[(MilestoneGanttStep, LocalDate, LocalDate) => Unit] = None,
  colors: GanttColors = DiffColors
) {

  private val layout = new GanttChartLayout(tgDateFormater, tgClock)

  private val axisSignal: Signal[GanttAxis] =
    milestoneRange
      .combineWith(workDays)
      .map { case (range, days) => range.fold(GanttAxis.empty) { case (start, end) => GanttAxis.of(start, end, days) } }
      .distinct

  private def stepRow(step: MilestoneGanttStep, index: Int, axis: GanttAxis): HtmlElement =
    div(
      cls("relative grid items-center border-b border-default-medium"),
      styleAttr := layout.gridTemplate(axis),
      div(
        cls(s"flex items-center self-stretch overflow-hidden px-2 py-0.5 ${layout.nameCellClasses}"),
        styleAttr := layout.nameCellStyle(),
        TgBadge(text = step.status.emoji, color = ChartColorBadgeColor.of(step.status), bordered = true).element
          .amend(cls("mr-1.5 shrink-0"), title := step.status.statusName),
        span(cls("min-w-0 truncate text-sm text-heading"), title := step.name, step.name)
      ),
      layout.dayCells(axis, "1"),
      layout.barOrOutside(
        step.startDate,
        step.dueDate,
        axis,
        "milestone",
        placed => {
          val bar = colors.bar(
            layout,
            layout.barTitle(step.name, step.startDate, step.dueDate),
            index,
            GanttBarProgress(step.tasksTotal, step.tasksDone),
            placed
          )
          onDatesChange.fold(bar) { change =>
            GanttBarDrag.editable(
              layout,
              step.name,
              step.startDate,
              step.dueDate,
              axis,
              placed,
              (start, due) => change(step, start, due)
            )(bar)
          }
        }
      )
    )

  private def chart(axis: GanttAxis, rows: Seq[MilestoneGanttStep]): HtmlElement =
    if (axis.isEmpty) layout.emptyMessage("Pick a valid start and due date with at least one work day to see the Gantt chart.")
    else if (rows.isEmpty) layout.emptyMessage("The milestone has no steps to show on the Gantt chart.")
    else layout.frame(axis, rows.zipWithIndex.map { case (step, index) => stepRow(step, index, axis) }, "Steps")

  val element: HtmlElement =
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText("Gantt chart", TgCard.CardHeaderSize.H5),
        body = TgCard.HtmlCardContent(div(child <-- axisSignal.combineWith(steps).map { case (axis, rows) =>
          chart(axis, rows)
        }))
      )
      .element
      .amend(cls("w-full mb-4"))
}
