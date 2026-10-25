package com.togger.ui.project

import com.togger.dto.{FindTaskRespFull, FindTaskStatusResp, FindTimeboxResp}
import com.togger.ui.teamdaily.TeamDailyStatusOrder

import java.time.LocalDate
import java.util.UUID

/** What the project home picks out of the project, kept apart from the screen so it can be tested without a DOM. */
object ProjectHomeStats {

  /** A timebox has no status of its own, it is active while today lies within the dates of its latest version. */
  def activeTimeboxes(timeboxes: Seq[FindTimeboxResp], today: LocalDate): Seq[FindTimeboxResp] =
    timeboxes
      .filter(timebox =>
        !today.isBefore(timebox.latestVersion.startDate) && !today.isAfter(timebox.latestVersion.endDate)
      )
      .sortBy(_.latestVersion.endDate.toEpochDay)

  /** The statuses of tasks that are being worked on, in the order of the daily. */
  def inFlightStatuses(statuses: Seq[FindTaskStatusResp]): Seq[FindTaskStatusResp] =
    statuses
      .flatMap(status => TeamDailyStatusOrder.rank(status.name).map(_ -> status))
      .sortBy(_._1)
      .map(_._2)

  /** The tasks in the given statuses, grouped by status in the order of the statuses; statuses without tasks are left
    * out.
    */
  def tasksByStatus(
    statuses: Seq[FindTaskStatusResp],
    tasks: Seq[FindTaskRespFull]
  ): Seq[(FindTaskStatusResp, Seq[FindTaskRespFull])] = {
    val byStatusId: Map[UUID, Seq[FindTaskRespFull]] = tasks.groupBy(_.taskStatus.id)
    statuses.flatMap(status => byStatusId.get(status.id).map(status -> _.sortBy(_.name)))
  }
}
