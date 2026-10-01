package com.togger.ui.components

import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveSvgElement
import com.togger.ui.auth.TgHtmlTypes.HtmlDiv
import com.togger.ui.organization.TgIcons
import com.togger.ui.task.TgDateFormater
import org.scalajs.dom.SVGSVGElement

import java.time.LocalDate

/** A month-grid date picker: chevrons move between months, clicking a day reports it via `onPick`. Meant to be
  * dropped into a popup (e.g. a [[com.togger.ui.components.wysiwyg.WYSIWYGTextEditorSplit]] trigger), not used as a
  * standalone field — there is no text input of its own.
  */
case class CalendarPickerPopup(
  initialMonth: LocalDate,
  today: LocalDate,
  tgDateFormater: TgDateFormater,
  onPick: LocalDate => Unit,
  selected: Option[LocalDate] = None
) {

  private val monthVar = Var(initialMonth.withDayOfMonth(1))

  val element: HtmlDiv =
    div(
      cls("p-3 w-64 bg-neutral-primary-medium border border-default-medium rounded-base shadow-lg"),
      headerRow,
      weekdayRow,
      div(cls("grid grid-cols-7 gap-1"), children <-- monthVar.signal.map(daysOf))
    )

  private def headerRow: HtmlDiv =
    div(
      cls("flex items-center justify-between mb-2"),
      navButton(TgIcons.chevronLeftIcon, "Previous month", () => monthVar.update(_.minusMonths(1))),
      div(
        cls("text-sm font-semibold text-heading"),
        child.text <-- monthVar.signal.map(month => s"${tgDateFormater.monthName(month)} ${month.getYear}")
      ),
      navButton(TgIcons.chevronRightIcon, "Next month", () => monthVar.update(_.plusMonths(1)))
    )

  private def navButton(
    icon: String => ReactiveSvgElement[SVGSVGElement],
    label: String,
    action: () => Unit
  ): HtmlElement =
    button(
      `type`("button"),
      aria.label(label),
      cls("p-1 rounded-base hover:bg-neutral-tertiary-medium cursor-pointer"),
      onMouseDown.preventDefault --> Observer.empty,
      onClick.mapToUnit --> (_ => action()),
      icon("w-4 h-4")
    )

  private def weekdayRow: HtmlDiv =
    div(
      cls("grid grid-cols-7 gap-1 mb-1"),
      Seq("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")
        .map(label => div(cls("text-xs font-semibold text-center text-gray-500"), label))
    )

  private def daysOf(month: LocalDate): Seq[HtmlElement] = {
    val leadingDays = month.getDayOfWeek.getValue - 1
    val gridStart = month.minusDays(leadingDays.toLong)
    val totalCells = ((leadingDays + month.lengthOfMonth() + 6) / 7) * 7
    (0 until totalCells).map { offset =>
      val day = gridStart.plusDays(offset.toLong)
      dayCell(day, inCurrentMonth = day.getMonthValue == month.getMonthValue && day.getYear == month.getYear)
    }
  }

  private def dayCell(day: LocalDate, inCurrentMonth: Boolean): HtmlElement =
    button(
      `type`("button"),
      cls("text-xs rounded-base p-1.5 text-center cursor-pointer hover:bg-neutral-tertiary-medium"),
      cls(if (inCurrentMonth) "text-heading" else "text-gray-400"),
      cls(if (day == today) "font-bold ring-1 ring-brand-subtle" else ""),
      cls(if (selected.contains(day)) "bg-neutral-tertiary-medium" else ""),
      onMouseDown.preventDefault --> Observer.empty,
      onClick.mapToUnit --> (_ => onPick(day)),
      day.getDayOfMonth.toString
    )
}
