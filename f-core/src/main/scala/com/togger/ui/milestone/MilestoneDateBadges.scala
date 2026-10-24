package com.togger.ui.milestone

import com.raquo.laminar.api.L._
import com.togger.common.{TgClock, WorkDayCalendar}
import com.togger.task.TaskPriority
import com.togger.ui.components.TgBadgeColor
import com.togger.ui.task.TgDateFormater
import com.togger.ui.task.bulk._

import java.time.{DayOfWeek, LocalDate}
import java.util.UUID
import scala.util.Try

/** The start and due date of the milestone a form edits, as badges that open the same date popups the task badges use.
  * The form keeps the dates as text, so the popups read and write them through [[dates]] and [[onDates]].
  */
class MilestoneDateBadges(
  dates: Signal[(String, String)],
  currentDates: () => (String, String),
  currentWorkDays: () => Set[DayOfWeek],
  onDates: (LocalDate, LocalDate) => Unit,
  tgClock: TgClock,
  endDate: Signal[String] = Val(""),
  daysLeftWorkDays: Option[Signal[Set[DayOfWeek]]] = None
) {

  private val key = UUID.randomUUID()
  private val tgDateFormater = new TgDateFormater

  private def parse(text: String): Option[LocalDate] = Try(LocalDate.parse(text)).toOption

  private def asTask(texts: (String, String)): BulkTask = {
    val (start, due) = texts
    val startDate = parse(start).getOrElse(tgClock.currentDate())
    val dueDate = parse(due).getOrElse(startDate)
    BulkTask
      .withDefaults(key, "", BulkTaskDefaults(TaskPriority.Medium, None, startDate, dueDate))
      .copy(dueDateManual = true)
  }

  private def lookups(): BulkLookups = BulkLookups(Seq.empty, Seq.empty, Seq.empty, Seq.empty, currentWorkDays())

  private val popups = new BulkEditPopups(
    context = _ => BulkEditContext(lookups(), Seq(asTask(currentDates())), tgClock.currentDate()),
    onEdit = (_, edit) => {
      val edited = BulkTaskEditor(Seq(asTask(currentDates())), Set(key), edit, currentWorkDays()).head
      onDates(edited.startDate, edited.dueDate)
    }
  )

  private def badge(date: Option[LocalDate], label: String, kind: BulkEditKind): BadgeSpec =
    BadgeSpec(
      s"$label ${date.map(tgDateFormater.asAmericanDateWithDayAbrv).getOrElse("-")}",
      TgBadgeColor.Gray,
      Some(kind)
    )

  /** The end date is set by closing the milestone, so its badge has no popup of its own. */
  private def endBadge(date: LocalDate): BadgeSpec =
    BadgeSpec(s"End ${tgDateFormater.asAmericanDateWithDayAbrv(date)}", TgBadgeColor.Success)

  private def daysLeft(due: Option[LocalDate], workDays: Set[DayOfWeek]): Option[BadgeSpec] =
    due.map { dueDate =>
      val today = tgClock.currentDate()
      if (dueDate.isBefore(today)) BadgeSpec("Overdue", TgBadgeColor.Red)
      else BadgeSpec(s"${WorkDayCalendar.countWorkDays(today, dueDate, workDays)} days left", TgBadgeColor.Gray)
    }

  val element: HtmlElement =
    div(
      cls("flex flex-wrap items-center gap-1.5"),
      children <-- dates.combineWith(endDate, daysLeftWorkDays.getOrElse(Val(Set.empty[DayOfWeek]))).map {
        case (start, due, end, workDays) =>
        BulkTaskCard.row(
          key,
          Seq(
            badge(parse(start), "Start", BulkEditKind.StartDate),
            badge(parse(due), "Due", BulkEditKind.DueDate)
          ) ++ parse(end).map(endBadge) ++ Option
            .when(daysLeftWorkDays.isDefined && parse(end).isEmpty)(daysLeft(parse(due), workDays))
            .flatten,
          popups
        )
      }
    )
}
