package com.togger.ui.task

import com.togger.dto.FindTaskStatusResp
import com.togger.task.DefaultTaskStatus
import com.togger.ui.teamdaily.TeamDailyStatusOrder

import java.time.{DayOfWeek, LocalDate}

/** What closing a task needs to know: the status it moves to and whether the end date picked for it is acceptable. */
object TaskClose {

  /** The status a closed task gets, recognised by its default name as statuses have no type of their own. */
  def doneStatus(statuses: Seq[FindTaskStatusResp]): Option[FindTaskStatusResp] =
    statuses.find(status =>
      TeamDailyStatusOrder.normalise(status.name) == TeamDailyStatusOrder.normalise(DefaultTaskStatus.Done.statusName)
    )

  def endDateErrors(endDate: Option[LocalDate], startDate: LocalDate, workDays: Set[DayOfWeek]): Seq[String] =
    endDate.fold(Seq("Pick an end date")) { date =>
      Seq(
        Option.when(date.isBefore(startDate))("End date must be on or after start date"),
        Option.when(!workDays.contains(date.getDayOfWeek))("End date must be one of this project's configured work days")
      ).flatten
    }
}
