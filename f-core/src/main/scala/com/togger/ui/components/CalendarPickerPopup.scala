package com.togger.ui.components

import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveSvgElement
import com.togger.ui.auth.TgHtmlTypes.HtmlDiv
import com.togger.ui.auth.TgInput.tgInputStyle
import com.togger.ui.organization.TgIcons
import com.togger.ui.task.TgDateFormater
import org.scalajs.dom
import org.scalajs.dom.SVGSVGElement

import java.time.LocalDate

/** A Flowbite-style date picker: a text input (free-typed date, or a quick-pick like "Tomorrow", filtered the same way
  * [[com.togger.ui.task.quick.CreateTaskQuick]] filters its token dropdowns) above a month-grid calendar. Picking a
  * date — by typing+Enter, choosing a quick-pick, or clicking a day — reports it via `onPick` and then always calls
  * `onClose`; clicking outside the popup or pressing Escape calls `onClose` without picking anything. Meant to be
  * dropped into a popup slot the caller owns (e.g. opened by clicking a badge), not mounted standalone.
  */
case class CalendarPickerPopup(
  initialMonth: LocalDate,
  today: LocalDate,
  tgDateFormater: TgDateFormater,
  onPick: LocalDate => Unit,
  onClose: () => Unit,
  selected: Option[LocalDate] = None,
  minDate: Option[LocalDate] = None,
  maxDate: Option[LocalDate] = None
) {

  private val monthVar = Var(initialMonth.withDayOfMonth(1))
  private val inputVar = Var("")
  private val highlightedVar = Var(0)

  private def inRange(day: LocalDate): Boolean =
    !minDate.exists(day.isBefore) && !maxDate.exists(day.isAfter)

  private def matches(query: String, label: String): Boolean =
    query.trim.isEmpty || label.toLowerCase.contains(query.trim.toLowerCase)

  /** Hidden once the typed text is itself a full valid date, so the suggestion list doesn't fight the live calendar
    * sync that a parseable date drives.
    */
  private def suggestionsFor(raw: String): Seq[DateSuggestion] =
    if (DatePickerInput.parse(raw, today).isDefined) Seq.empty
    else
      DatePickerSuggestions.all.filter(suggestion =>
        matches(raw, suggestion.label) && inRange(suggestion.resolve(today))
      )

  private def commit(day: LocalDate): Unit = {
    onPick(day)
    onClose()
  }

  private def onInputChange(raw: String): Unit = {
    inputVar.set(raw)
    highlightedVar.set(0)
    DatePickerInput.parse(raw, today).filter(inRange).foreach(day => monthVar.set(day.withDayOfMonth(1)))
  }

  private def commitHighlightedOrTyped(): Unit = {
    val options = suggestionsFor(inputVar.now())
    if (options.nonEmpty) commit(options(highlightedVar.now().min(options.size - 1)).resolve(today))
    else DatePickerInput.parse(inputVar.now(), today).filter(inRange).foreach(commit)
  }

  private def onInputKeyDown(event: dom.KeyboardEvent): Unit =
    event.key match {
      case "Escape" =>
        event.stopPropagation()
        onClose()
      case "ArrowDown" | "ArrowUp" if suggestionsFor(inputVar.now()).nonEmpty =>
        event.preventDefault()
        val options = suggestionsFor(inputVar.now())
        val step = if (event.key == "ArrowDown") 1 else -1
        highlightedVar.update(index => (index + step + options.size) % options.size)
      case "Enter" =>
        event.preventDefault()
        commitHighlightedOrTyped()
      case _ => ()
    }

  private val inputElement: HtmlElement =
    input(
      `type`("text"),
      cls(tgInputStyle),
      cls("text-sm"),
      aria.label("Date"),
      onMountCallback { ctx =>
        ctx.thisNode.ref.focus()
        ctx.thisNode.ref.select()
      },
      controlled(
        value <-- inputVar.signal,
        onInput.mapToValue --> (raw => onInputChange(raw))
      ),
      onKeyDown --> (event => onInputKeyDown(event))
    )

  private def errorMessage(raw: String): Option[String] =
    if (raw.trim.isEmpty || suggestionsFor(raw).nonEmpty) None
    else
      DatePickerInput.parse(raw, today) match {
        case Some(day) if !inRange(day) => Some("That date is outside the allowed range")
        case Some(_)                    => None
        case None                       => Some("Enter a valid date (yyyy-mm-dd)")
      }

  private def suggestionsPopup: Signal[Option[HtmlElement]] =
    inputVar.signal.map(suggestionsFor).map { current =>
      if (current.isEmpty) None
      else
        Some(
          QuickOptionsPopup(
            options = current.zipWithIndex.map { case (suggestion, index) =>
              QuickOption(
                id = index.toString,
                label = suggestion.label,
                description = tgDateFormater.asAmericanDate(suggestion.resolve(today)),
                isSelected = false
              )
            },
            isHighlighted = index => highlightedVar.signal.map(_ == index).distinct,
            onChoose = option => commit(current(option.id.toInt).resolve(today))
          ).element
        )
    }

  val element: HtmlDiv =
    div(
      cls("absolute z-10 mt-1 p-3 w-64 bg-neutral-primary-medium border border-default-medium rounded-base shadow-lg"),
      onMountBind { ctx =>
        val rootNode = ctx.thisNode.ref
        documentEvents(_.onClick)
          .filter(event => !rootNode.contains(event.target.asInstanceOf[dom.Node]))
          .mapToUnit --> (_ => onClose())
      },
      div(
        cls("relative mb-3"),
        inputElement,
        child.maybe <-- suggestionsPopup,
        child.maybe <-- inputVar.signal
          .map(errorMessage)
          .map(
            _.map(message => p(cls("mt-1 text-xs text-fg-danger-strong"), message))
          )
      ),
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

  private def dayCell(day: LocalDate, inCurrentMonth: Boolean): HtmlElement = {
    val outOfRange = !inRange(day)
    button(
      `type`("button"),
      cls("text-xs rounded-base p-1.5 text-center"),
      cls(if (outOfRange) "cursor-not-allowed text-gray-300" else "cursor-pointer hover:bg-neutral-tertiary-medium"),
      cls(if (inCurrentMonth && !outOfRange) "text-heading" else if (!outOfRange) "text-gray-400" else ""),
      cls(if (day == today) "font-bold ring-1 ring-brand-subtle" else ""),
      cls(if (selected.contains(day)) "bg-neutral-tertiary-medium" else ""),
      disabled(outOfRange),
      onMouseDown.preventDefault --> Observer.empty,
      onClick.mapToUnit --> (_ => if (!outOfRange) commit(day)),
      day.getDayOfMonth.toString
    )
  }
}
