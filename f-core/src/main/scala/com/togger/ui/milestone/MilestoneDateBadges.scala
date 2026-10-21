package com.togger.ui.milestone

import com.raquo.laminar.api.L._
import com.togger.common.TgClock
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
  tgClock: TgClock
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

  val element: HtmlElement =
    div(
      cls("flex flex-wrap items-center gap-1.5"),
      children <-- dates.map { case (start, due) =>
        BulkTaskCard.row(
          key,
          Seq(
            badge(parse(start), "Start", BulkEditKind.StartDate),
            badge(parse(due), "Due", BulkEditKind.DueDate)
          ),
          popups
        )
      }
    )
}
