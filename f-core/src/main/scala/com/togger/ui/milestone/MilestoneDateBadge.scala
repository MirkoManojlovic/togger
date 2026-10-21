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

/** The date of one additional milestone date, as a badge that opens the date popup the task badges use. The popup is the
  * due date one of a task starting on the milestone's start date, so it offers no day before it; the date is optional,
  * so it can fall on any day of the week.
  */
class MilestoneDateBadge(
  date: Signal[String],
  currentDate: () => String,
  milestoneStartDate: () => String,
  onDate: LocalDate => Unit,
  tgClock: TgClock
) {

  private val key = UUID.randomUUID()
  private val tgDateFormater = new TgDateFormater
  private val everyDay = DayOfWeek.values.toSet

  private def parse(text: String): Option[LocalDate] = Try(LocalDate.parse(text)).toOption

  private def asTask(): BulkTask = {
    val start = parse(milestoneStartDate()).getOrElse(tgClock.currentDate())
    val due = parse(currentDate()).getOrElse(start)
    BulkTask.withDefaults(key, "", BulkTaskDefaults(TaskPriority.Medium, None, start, due))
  }

  private val popups = new BulkEditPopups(
    context = _ =>
      BulkEditContext(
        BulkLookups(Seq.empty, Seq.empty, Seq.empty, Seq.empty, everyDay),
        Seq(asTask()),
        tgClock.currentDate()
      ),
    onEdit = (_, edit) => {
      val edited = BulkTaskEditor(Seq(asTask()), Set(key), edit, everyDay).head
      onDate(edited.dueDate)
    }
  )

  val element: HtmlElement =
    div(
      cls("flex flex-wrap items-center gap-1.5"),
      children <-- date.map { text =>
        BulkTaskCard.row(
          key,
          Seq(
            BadgeSpec(
              s"Date ${parse(text).map(tgDateFormater.asAmericanDateWithDayAbrv).getOrElse("-")}",
              TgBadgeColor.Gray,
              Some(BulkEditKind.DueDate)
            )
          ),
          popups
        )
      }
    )
}
