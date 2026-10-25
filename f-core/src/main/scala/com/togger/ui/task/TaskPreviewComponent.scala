package com.togger.ui.task

import com.raquo.laminar.api.L._
import com.togger.common.TgClock
import com.togger.dto.{FindMilestoneResp, FindTaskRespFull, LogEntryDto}
import com.togger.markdown.TgMarkdown
import com.togger.routes.Pages.EditTaskPage
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.ui.components.LogEntryStatusBadgeColor.LogEntryStatusBadgeColorOps
import com.togger.ui.components._
import com.togger.ui.logentry.LogEntryMemberBadge
import com.togger.ui.project.ProjectHomeStats
import com.togger.ui.task.bulk.{BadgeSpec, BulkTaskCard}
import com.togger.ui.teamdaily.TeamDailyTaskBadges
import com.togger.ui.timebox.gantt.ReviewTaskBadges

import java.time.{DayOfWeek, LocalDate, ZoneOffset}

/** A task as the status of the projects shows it: its description on the left, its log entries in the middle, by the
  * day selected in the calendar or by a range of weeks, and the calendar of the log entries on the right.
  *
  * @param showName
  *   the name of the task, linked to its page, with its labels beside it and the other badges of the task under it, over the description; left out
  *   where the name is shown by the caller
  * @param milestones
  *   to name the milestone of the task in its badges
  */
class TaskPreviewComponent(
  task: FindTaskRespFull,
  workDays: Set[DayOfWeek],
  router: TgRouter[FrontendPage],
  tgDateFormater: TgDateFormater,
  tgMarkdown: TgMarkdown,
  tgClock: TgClock,
  showName: Boolean = false,
  milestones: Seq[FindMilestoneResp] = Seq.empty
) {

  private val currentWeekStart: LocalDate = {
    val today = tgClock.currentDate()
    today.minusDays(today.getDayOfWeek.getValue - 1L)
  }
  private val currentWeekEnd: LocalDate = currentWeekStart.plusDays(6L)
  private val lastTwoWeeksStart: LocalDate = currentWeekStart.minusDays(7L)
  private val lastTwoWeeksEnd: LocalDate = currentWeekEnd

  private val selectedDayVar: Var[Option[LocalDate]] =
    Var(LogEntryCalendarComponent.lastCalendarDay(task.logEntries, workDays))

  private def nameWithLabels: HtmlElement =
    div(
      cls("mb-2 flex flex-wrap items-center gap-2"),
      h3(
        cls("text-lg font-medium"),
        a(
          cls("text-fg-brand hover:underline"),
          task.name,
          router.navigateTo(EditTaskPage(task.projectId, task.id))
        )
      ),
      BulkTaskCard.readOnlyRow(ProjectHomeStats.labelBadges(task)),
      TaskProgressComponent
        .fromDescription(Val(task.description), ringSizeCls = "w-3 h-3", textSizeCls = "text-xs")
        .element
        .amend(cls("self-center leading-none"))
    )

  /** The milestone, priority, status and assignee, and the dates with the work days left, in a row each. */
  private def badges: HtmlElement =
    div(
      cls("flex flex-col gap-1.5"),
      ReviewTaskBadges.primary(task, milestones),
      BulkTaskCard.readOnlyRow(
        Seq(
          BadgeSpec(tgDateFormater.asAmericanDate(task.startDate), TgBadgeColor.Gray),
          BadgeSpec(tgDateFormater.asAmericanDate(task.dueDate), TgBadgeColor.Gray),
          TeamDailyTaskBadges.daysLeft(task.dueDate, tgClock.currentDate(), workDays)
        )
      )
    )

  val element: HtmlElement =
    div(
      cls("flex flex-col md:flex-row gap-4 items-start"),
      div(
        cls("flex-1 min-w-0"),
        Option.when(showName)(
          div(
            cls("mb-3 p-2 border border-default rounded-base bg-neutral-primary-soft"),
            nameWithLabels,
            badges
          )
        ),
        tgMarkdown.toLaminarDiv(task.description)
      ),
      // The tabs stay in one row: the column is at least as wide as the row of tabs, and the other columns give way.
      div(
        cls("flex-1 min-w-0 md:min-w-min"),
        cls("[&_[role=tablist]]:flex-nowrap [&_[role=tab]]:whitespace-nowrap"),
        weekRangeStatusTabs(task.logEntries, selectedDayVar)
      ),
      div(
        cls("w-fit"),
        new LogEntryCalendarComponent(task.logEntries, tgDateFormater, workDays, selectedDayVar).element
      )
    )

  private def selectedDayStatus(logEntries: Seq[LogEntryDto], selectedDayVar: Var[Option[LocalDate]]) =
    div(
      child <-- selectedDayVar.signal.map {
        case None => emptyNode
        case Some(day) =>
          val dayEntries = logEntries
            .filter(entry => entry.createdAt.atOffset(ZoneOffset.UTC).toLocalDate == day)
            .sortBy(_.createdAt)

          def dateBadge() =
            TgBadge(
              text = tgDateFormater.asAmericanDateWithDayAbrv(day),
              bordered = true,
              color = TgBadgeColor.Gray
            ).element

          div(
            cls("mb-4"),
            if (dayEntries.isEmpty)
              div(
                div(cls("flex items-center gap-2 mb-2"), dateBadge()),
                p(cls("text-body"), "No status update")
              )
            else
              dayEntries.map(entry =>
                div(
                  cls("mb-2"),
                  div(
                    cls("flex items-center gap-2 mb-2"),
                    dateBadge(),
                    TgBadge(text = entry.status.toStringWithEmoji, color = entry.status.badgeColor, bordered = true).element,
                    LogEntryMemberBadge(entry.createdByName)
                  ),
                  tgMarkdown.toLaminarDiv(entry.textExternal)
                )
              )
          )
      }
    )

  private def weekRangeStatus(
    logEntries: Seq[LogEntryDto],
    start: LocalDate,
    end: LocalDate,
    selectedDayVar: Var[Option[LocalDate]]
  ) = {
    val entriesByDate = logEntries
      .filter { entry =>
        val day = entry.createdAt.atOffset(ZoneOffset.UTC).toLocalDate
        !day.isBefore(start) && !day.isAfter(end)
      }
      .groupBy(entry => entry.createdAt.atOffset(ZoneOffset.UTC).toLocalDate)

    div(
      cls("mb-4"),
      if (entriesByDate.isEmpty) p(cls("text-body"), "No status update")
      else
        entriesByDate.toSeq.sortBy(_._1).map { case (day, dayEntries) =>
          div(
            cls("mb-2 p-2 border rounded-base"),
            cls.toggle("border-brand") <-- selectedDayVar.signal.map(_.contains(day)),
            cls.toggle("border-transparent") <-- selectedDayVar.signal.map(!_.contains(day)),
            dayEntries
              .sortBy(_.createdAt)
              .map(entry =>
                div(
                  cls("mb-2"),
                  div(
                    cls("flex items-center gap-2 mb-2"),
                    TgBadge(
                      text = tgDateFormater.asAmericanDateWithDayAbrv(day),
                      bordered = true,
                      color = TgBadgeColor.Gray
                    ).element,
                    TgBadge(text = entry.status.toStringWithEmoji, color = entry.status.badgeColor, bordered = true).element,
                    LogEntryMemberBadge(entry.createdByName)
                  ),
                  tgMarkdown.toLaminarDiv(entry.textExternal)
                )
              )
          )
        }
    )
  }

  private def weekRangeStatusTabs(logEntries: Seq[LogEntryDto], selectedDayVar: Var[Option[LocalDate]]): HtmlElement = {
    val selectedTabVar = Var("single-day")
    val tabs = Seq(
      TgTab(
        id = "single-day",
        label = "Single day",
        content = selectedDayStatus(logEntries, selectedDayVar)
      ),
      TgTab(
        id = "last-week",
        label = "Last week",
        content = weekRangeStatus(logEntries, currentWeekStart, currentWeekEnd, selectedDayVar)
      ),
      TgTab(
        id = "last-2-weeks",
        label = "Last 2 weeks",
        content = weekRangeStatus(logEntries, lastTwoWeeksStart, lastTwoWeeksEnd, selectedDayVar)
      ),
      TgTab(
        id = "everything",
        label = "Everything",
        content = weekRangeStatus(logEntries, LocalDate.MIN, LocalDate.MAX, selectedDayVar)
      )
    )
    new TgTabs(tabs, selectedTabVar).element
  }
}
