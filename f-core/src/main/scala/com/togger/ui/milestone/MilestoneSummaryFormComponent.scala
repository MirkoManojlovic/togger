package com.togger.ui.milestone

import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveHtmlElement
import com.togger.common.TgClock
import com.togger.dto.FindMilestoneSummaryResp
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.TgInputType
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

  private val startDateInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.startDate),
    errors = EventStream.empty,
    labelValue = "Start date",
    tgInputType = TgInputType.Date
  )

  private val endDateInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.endDate),
    errors = EventStream.empty,
    labelValue = "End date",
    tgInputType = TgInputType.Date
  )

  private def presetButton(preset: MilestoneSummaryPreset): HtmlElement = {
    val range = MilestoneSummaryPresets.range(preset, tgClock.currentDate())
    button(
      typ("button"),
      cls(MilestoneSummaryFormComponent.presetButtonCls),
      disabled <-- workDays.map(days => days.nonEmpty && MilestoneSummaryPresets.misaligned(range, days)),
      preset.label,
      onClick.mapToUnit --> (_ =>
        formState.update(_.copy(startDate = range._1.toString, endDate = range._2.toString))
      )
    )
  }

  val htmlElement: ReactiveHtmlElement[HTMLDivElement] =
    div(
      cls("mb-4"),
      div(
        cls("mb-4 flex flex-wrap items-end gap-4"),
        startDateInput.htmlElement,
        endDateInput.htmlElement,
        div(cls("flex gap-1.5 pb-1"), MilestoneSummaryPreset.all.map(presetButton)),
        div(
          cls("ml-auto pb-1"),
          iconButton(
            "Delete summary",
            TgIcons.trashIcon(className = "w-4 h-4"),
            rowIconButtonDangerCls,
            rowButtonMarginCls,
            onClick.mapToUnit --> (_ => onRemove(this))
          )
        )
      ),
      summaryEditor.htmlElement
    )

  override def toString = s"MilestoneSummaryFormComponent(${formState.now()})"
}

object MilestoneSummaryFormComponent {
  val presetButtonCls =
    "px-2.5 py-1 text-xs font-medium rounded-lg border border-default hover:bg-neutral-secondary-soft " +
      "disabled:opacity-50 disabled:cursor-not-allowed"
}
