package com.togger.ui.milestone

import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveHtmlElement
import com.togger.common.TgClock
import com.togger.components.button.TgHtmlButton
import com.togger.dto.FindMilestoneSummaryResp
import com.togger.ui.components.{TgBadge, TgBadgeColor}
import com.togger.ui.components.wysiwyg.WYSIWYGTextEditorSplit.WYSIWYGTextEditorSplitSyntax
import com.togger.ui.organization.TgIcons
import com.togger.ui.task.TaskConfigTableSupport.{iconButton, rowButtonMarginCls, rowIconButtonDangerCls}
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro
import org.scalajs.dom.HTMLFormElement

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

/** The form of one summary, shared by the saved ones and the new one; it saves itself, apart from the milestone. */
class MilestoneSummaryFormComponent(
  initial: MilestoneSummaryForm,
  tgClock: TgClock,
  onSave: MilestoneSummaryFormComponent => Unit,
  onRemove: MilestoneSummaryFormComponent => Unit
) {
  val formState = Var(initial)

  private val summaryEditor = formState.wysiwygSplitEditorSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.summary),
    labelValue = "Summary",
    placeholderValue = "Summarize the period",
    minHeightRem = 18,
    minWidthRem = 0
  )

  private val everyDay = DayOfWeek.values.toSet

  // The dates are the start and "due" of the badges, which are labelled Start and End here.
  private val dateBadges = new MilestoneDateBadges(
    dates = formState.signal.map(form => form.startDate -> form.endDate),
    currentDates = () => formState.now().startDate -> formState.now().endDate,
    currentWorkDays = () => everyDay,
    onDates = (start, end) => formState.update(_.copy(startDate = start.toString, endDate = end.toString)),
    tgClock = tgClock,
    dueLabel = "End"
  )

  private def presetBadge(preset: MilestoneSummaryPreset): HtmlElement = {
    val range = MilestoneSummaryPresets.range(preset, tgClock.currentDate())
    button(
      typ("button"),
      cls("rounded-base cursor-pointer hover:ring-2 hover:ring-blue-300"),
      title(s"${range._1} - ${range._2}"),
      TgBadge(text = preset.label, color = TgBadgeColor.Brand).element,
      onClick.mapToUnit --> (_ => formState.update(_.copy(startDate = range._1.toString, endDate = range._2.toString)))
    )
  }

  private val isSaved: Signal[Boolean] = formState.signal.map(_.id.isDefined).distinct

  private val deleteButton: HtmlElement =
    iconButton(
      "Delete summary",
      TgIcons.trashIcon(className = "w-4 h-4"),
      rowIconButtonDangerCls,
      rowButtonMarginCls,
      onClick.mapToUnit --> (_ => onRemove(this))
    )

  val htmlElement: ReactiveHtmlElement[HTMLFormElement] =
    form(
      onSubmit.preventDefault.mapToUnit --> (_ => onSave(this)),
      div(
        cls("mb-2 flex flex-wrap items-center gap-1.5"),
        dateBadges.element,
        div(cls("ml-auto"), child.maybe <-- isSaved.map(Option.when(_)(deleteButton)))
      ),
      div(cls("mb-4 flex flex-wrap items-center gap-1.5"), MilestoneSummaryPreset.all.map(presetBadge)),
      summaryEditor.htmlElement.amend(cls("mb-4")),
      div(
        cls("flex justify-end gap-2"),
        child <-- isSaved.map(saved =>
          if (saved) TgHtmlButton.updateSmall(EventStream.fromValue(false))
          else TgHtmlButton.createSmall(EventStream.fromValue(false))
        )
      )
    )

  override def toString = s"MilestoneSummaryFormComponent(${formState.now()})"
}
