package com.togger.ui.milestone

import com.raquo.laminar.api.L._
import com.togger.ui.auth.TgHtmlTypes.HtmlDiv

import java.time.{DayOfWeek, LocalDate}

/** Mini calendars of a date range, `Weeks` weeks each; the oldest on the left and the newest on the right. The days
  * of the range are filled, the others in the weeks shown are faded, and the first and the last day of the range are
  * marked. Two clicks pick a new range, which is handed to `onRange`, see [[pick]].
  */
object MilestoneSummaryRangeCalendar {

  val Weeks = 4

  private val DaysInWeek = 7

  /** The first day of each calendar: the Monday of the week the range starts in, then every `Weeks` weeks until the
    * end of the range is covered. Empty for a range that ends before it starts.
    */
  def calendarStarts(start: LocalDate, end: LocalDate): Seq[LocalDate] =
    if (end.isBefore(start)) Seq.empty
    else {
      val firstMonday = start.minusDays((start.getDayOfWeek.getValue - DayOfWeek.MONDAY.getValue).toLong)
      Iterator.iterate(firstMonday)(_.plusWeeks(Weeks.toLong)).takeWhile(!_.isAfter(end)).toSeq
    }

  /** What a click on `clicked` does while `pending` is the first day of a range being picked: with none pending it
    * starts a range of that one day, which stays pending; with one pending it ends the range there, the earlier of the
    * two days first.
    */
  def pick(pending: Option[LocalDate], clicked: LocalDate): ((LocalDate, LocalDate), Option[LocalDate]) =
    pending match {
      case None        => ((clicked, clicked), Some(clicked))
      case Some(first) => ((if (clicked.isBefore(first)) clicked else first, if (clicked.isBefore(first)) first else clicked), None)
    }

  def element(dates: Signal[(String, String)], onRange: (LocalDate, LocalDate) => Unit = (_, _) => ()): HtmlDiv = {
    // shared by the calendars, which are rebuilt with every change of the range, including the one the first click makes
    val pendingVar = Var(Option.empty[LocalDate])
    val onDay: LocalDate => Unit = day => {
      val ((start, end), pending) = pick(pendingVar.now(), day)
      pendingVar.set(pending)
      onRange(start, end)
    }
    div(
      cls("flex max-w-full gap-3 overflow-x-auto"),
      children <-- dates.map { case (start, end) =>
        parse(start, end).toSeq.flatMap { case (from, to) =>
          calendarStarts(from, to).map(calendar(_, from, to, onDay))
        }
      }
    )
  }

  private def parse(start: String, end: String): Option[(LocalDate, LocalDate)] =
    scala.util.Try(LocalDate.parse(start) -> LocalDate.parse(end)).toOption

  private def calendar(
    first: LocalDate,
    from: LocalDate,
    to: LocalDate,
    onDay: LocalDate => Unit
  ): HtmlDiv = {
    val days = Iterator.iterate(first)(_.plusDays(1)).take(Weeks * DaysInWeek).toSeq
    div(
      cls("shrink-0"),
      div(cls("mb-1 text-[11px] font-medium text-body"), title(s"${days.head} - ${days.last}"), heading(days)),
      div(
        styleAttr := s"display: grid; grid-template-columns: repeat($DaysInWeek, 1.5rem); gap: 0.125rem;",
        DayOfWeek.values.toSeq.map(day =>
          div(cls("text-center text-[10px] leading-4 font-semibold text-body-subtle"), day.name.take(1))
        ),
        days.map(dayCell(_, from, to, onDay))
      )
    )
  }

  private def heading(days: Seq[LocalDate]): String = {
    val months = days.map(day => monthAbbrev(day) -> day.getYear).distinct
    val (firstMonth, firstYear) = months.head
    val (lastMonth, lastYear) = months.last
    if (months.size == 1) s"$firstMonth $firstYear"
    else if (firstYear == lastYear) s"$firstMonth - $lastMonth $lastYear"
    else s"$firstMonth $firstYear - $lastMonth $lastYear"
  }

  private def monthAbbrev(day: LocalDate): String = day.getMonth.name.toLowerCase.capitalize.take(3)

  private def dayCell(day: LocalDate, from: LocalDate, to: LocalDate, onDay: LocalDate => Unit): HtmlDiv = {
    val inRange = !day.isBefore(from) && !day.isAfter(to)
    val (colorCls, tooltip) =
      if (day == from && day == to) ("bg-green-600 text-white font-bold", s"Start and end: $day")
      else if (day == from) ("bg-green-600 text-white font-bold", s"Start: $day")
      else if (day == to) ("bg-red-600 text-white font-bold", s"End: $day")
      else if (inRange) ("bg-brand text-white", day.toString)
      else ("bg-neutral-secondary-medium text-body-subtle opacity-60", day.toString)
    div(
      cls("flex h-6 w-6 cursor-pointer items-center justify-center rounded-sm text-[10px] leading-none"),
      cls(colorCls),
      title(tooltip),
      onClick.mapToUnit --> (_ => onDay(day)),
      day.getDayOfMonth.toString
    )
  }
}
