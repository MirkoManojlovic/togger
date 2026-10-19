package com.togger.ui.milestone

import com.raquo.laminar.api.L._
import com.togger.ui.auth.TgHtmlTypes.HtmlDiv
import com.togger.ui.auth.{TgInput, TgInputType}
import com.togger.ui.components.{MultiSelectItem, TableFilterComponent}
import com.togger.ui.task.TgDateFormater

import java.time.LocalDate
import scala.util.Try

case class MonthOption(year: Int, month: Int) {
  def id: String = s"$year-$month"
}

sealed trait DateFilterMode
object DateFilterMode {
  case object Month extends DateFilterMode
  case object Range extends DateFilterMode
}

sealed trait ActiveDateFilter
object ActiveDateFilter {
  // empty selection means "no filter" (show every month), mirroring TableFilterComponent's existing
  // convention elsewhere in the codebase (e.g. ShowMilestonesComponent's status filter).
  case class ByMonth(selected: Set[(Int, Int)]) extends ActiveDateFilter
  case class ByRange(start: Option[LocalDate], end: Option[LocalDate]) extends ActiveDateFilter
}

/** @param initialMonths
  *   the months ticked at first, `None` meaning all of them
  */
class DateFilterComponent(
  monthOptionsSignal: Signal[Seq[MonthOption]],
  tgDateFormater: TgDateFormater,
  initialMode: DateFilterMode,
  initialMonths: Option[Set[String]],
  initialStart: Option[LocalDate],
  initialStop: Option[LocalDate]
) {

  private val modeVar: Var[DateFilterMode] = Var(initialMode)

  val monthItemsSignal: Signal[Seq[MultiSelectItem]] =
    monthOptionsSignal.map(_.map { opt =>
      MultiSelectItem(
        id = opt.id,
        name = s"${tgDateFormater.monthName(LocalDate.of(opt.year, opt.month, 1))} ${opt.year}",
        isSelected = initialMonths.forall(_.contains(opt.id))
      )
    })

  private val monthFilterComponent = new TableFilterComponent(monthItemsSignal, label = "Months")

  private def parseMonthId(id: String): Option[(Int, Int)] =
    id.split("-", 2) match {
      case Array(year, month) => Try((year.toInt, month.toInt)).toOption
      case _ => None
    }

  private val monthSelectionSignal: Signal[Set[(Int, Int)]] =
    monthFilterComponent.selectedIds.map(_.flatMap(parseMonthId))

  private val startDateVar: Var[String] = Var(initialStart.fold("")(_.toString))
  private val stopDateVar: Var[String] = Var(initialStop.fold("")(_.toString))

  private def parseDate(value: String): Option[LocalDate] =
    if (value.isEmpty) None else Try(LocalDate.parse(value)).toOption

  private val startDateInput = TgInput(
    inputSignal = startDateVar.signal,
    inputStateUpdater = startDateVar.updater[String]((_, newValue) => newValue),
    $errors = EventStream.empty,
    labelValue = "Start date",
    placeholderValue = "",
    tgInputType = TgInputType.Date,
    isRequired = false
  )

  private val stopDateInput = TgInput(
    inputSignal = stopDateVar.signal,
    inputStateUpdater = stopDateVar.updater[String]((_, newValue) => newValue),
    $errors = EventStream.empty,
    labelValue = "Stop date",
    placeholderValue = "",
    tgInputType = TgInputType.Date,
    isRequired = false
  )

  private val rangeSelectionSignal: Signal[(Option[LocalDate], Option[LocalDate])] =
    startDateVar.signal.combineWith(stopDateVar.signal).map { case (start, stop) =>
      (parseDate(start), parseDate(stop))
    }

  val modeSignal: Signal[DateFilterMode] = modeVar.signal

  val selectedMonthIds: Signal[Set[String]] = monthFilterComponent.selectedIds

  val rangeSignal: Signal[(Option[LocalDate], Option[LocalDate])] = rangeSelectionSignal

  val activeFilter: Signal[ActiveDateFilter] =
    modeVar.signal.combineWith(monthSelectionSignal, rangeSelectionSignal).map {
      case (DateFilterMode.Month, monthSelection, _) => ActiveDateFilter.ByMonth(monthSelection)
      case (DateFilterMode.Range, _, (start, end)) => ActiveDateFilter.ByRange(start, end)
    }

  private val modeButtonBaseCls =
    "px-3 py-1.5 text-sm font-medium rounded-base border border-default-medium cursor-pointer"

  private def modeButton(mode: DateFilterMode, label: String) =
    button(
      `type` := "button",
      cls(modeButtonBaseCls),
      cls <-- modeVar.signal.map(current =>
        if (current == mode) "bg-brand text-white" else "bg-neutral-secondary-medium text-body"
      ),
      onClick.mapToUnit --> (_ => modeVar.set(mode)),
      label
    )

  val element: HtmlDiv =
    div(
      cls("flex items-center gap-3 mb-4"),
      div(cls("flex items-center gap-2"), modeButton(DateFilterMode.Month, "Month"), modeButton(DateFilterMode.Range, "Date range")),
      child <-- modeVar.signal.map {
        case DateFilterMode.Month =>
          monthFilterComponent.element
        case DateFilterMode.Range =>
          div(
            cls("flex items-center gap-2"),
            startDateInput.rawHtmlElement,
            span(cls("text-body"), "–"),
            stopDateInput.rawHtmlElement
          )
      }
    )

}
