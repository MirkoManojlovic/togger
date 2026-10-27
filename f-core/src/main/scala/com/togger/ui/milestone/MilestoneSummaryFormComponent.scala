package com.togger.ui.milestone

import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveHtmlElement
import com.togger.common.TgClock
import com.togger.dto.FindMilestoneSummaryResp
import com.togger.ui.components.{TgBadge, TgBadgeColor}
import com.togger.ui.components.wysiwyg.WYSIWYGTextEditorSplit.WYSIWYGTextEditorSplitSyntax
import com.togger.ui.organization.TgIcons
import com.togger.ui.task.TaskConfigTableSupport.{iconButton, rowButtonMarginCls, rowIconButtonDangerCls}
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro
import org.scalajs.dom.HTMLDivElement

import java.time.DayOfWeek
import java.util.UUID

/** `id` is empty until the summary is saved. */
case class MilestoneSummaryForm(
  id: Option[UUID] = None,
  summary: String = "",
  startDate: String = "",
  endDate: String = ""
)

object MilestoneSummaryForm {
  def fromDto(dto: FindMilestoneSummaryResp): MilestoneSummaryForm =
    MilestoneSummaryForm(
      id = Some(dto.id),
      summary = dto.summary,
      startDate = dto.startDate.toString,
      endDate = dto.endDate.toString
    )
}

/** The form of one summary, shared by the saved and the not yet saved ones. */
class MilestoneSummaryFormComponent(
  initial: MilestoneSummaryForm,
  workDays: Signal[Set[DayOfWeek]],
  tgClock: TgClock,
  onRemove: MilestoneSummaryFormComponent => Unit
) {
  val formState = Var(initial)

  private val summaryEditor = formState.wysiwygSplitEditorSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.summary),
    labelValue = "Summary",
    placeholderValue = "Summarize the period",
    minHeightRem = 8,
    minWidthRem = 0
  )

  private val workDaysVar = Var(Set.empty[DayOfWeek])

  // The dates are the start and "due" of the badges, which are labelled Start and End here.
  private val dateBadges = new MilestoneDateBadges(
    dates = formState.signal.map(form => form.startDate -> form.endDate),
    currentDates = () => formState.now().startDate -> formState.now().endDate,
    currentWorkDays = () => workDaysVar.now(),
    onDates = (start, end) => formState.update(_.copy(startDate = start.toString, endDate = end.toString)),
    tgClock = tgClock,
    dueLabel = "End"
  )

  private def presetBadge(preset: MilestoneSummaryPreset): HtmlElement = {
    val range = MilestoneSummaryPresets.range(preset, tgClock.currentDate())
    val isDisabled = workDays.map(days => days.nonEmpty && MilestoneSummaryPresets.misaligned(range, days))
    button(
      typ("button"),
      cls("rounded-base"),
      cls("cursor-pointer hover:ring-2 hover:ring-blue-300") <-- isDisabled.map(!_),
      cls("opacity-50 cursor-not-allowed") <-- isDisabled,
      disabled <-- isDisabled,
      title(s"${range._1} - ${range._2}"),
      TgBadge(text = preset.label, color = TgBadgeColor.Brand).element,
      onClick.mapToUnit --> (_ => formState.update(_.copy(startDate = range._1.toString, endDate = range._2.toString)))
    )
  }

  val htmlElement: ReactiveHtmlElement[HTMLDivElement] =
    div(
      cls("mb-4"),
      workDays --> workDaysVar,
      div(
        cls("mb-2 flex flex-wrap items-center gap-1.5"),
        dateBadges.element,
        div(
          cls("ml-auto"),
          iconButton(
            "Delete summary",
            TgIcons.trashIcon(className = "w-4 h-4"),
            rowIconButtonDangerCls,
            rowButtonMarginCls,
            onClick.mapToUnit --> (_ => onRemove(this))
          )
        )
      ),
      div(cls("mb-4 flex flex-wrap items-center gap-1.5"), MilestoneSummaryPreset.all.map(presetBadge)),
      summaryEditor.htmlElement
    )

  override def toString = s"MilestoneSummaryFormComponent(${formState.now()})"
}
