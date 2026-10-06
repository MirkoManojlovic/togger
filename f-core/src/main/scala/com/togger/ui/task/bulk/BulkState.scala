package com.togger.ui.task.bulk

import com.togger.common.WorkDayCalendar
import com.togger.dto.organization.OrganizationProjectResp
import com.togger.ui.task.TaskProjectData

import java.time.{DayOfWeek, LocalDate}
import java.util.UUID

/** @param awaitingRestoredData
  *   the tasks come from a saved draft and the project data they refer to has not arrived yet. Until it has, a change
  *   of the defaults (the project's work days and statuses arriving) must not reset the attributes of the restored
  *   tasks, and once it has, whatever no longer exists is dropped from them.
  */
case class BulkState(
  today: LocalDate,
  projectId: Option[UUID] = None,
  projects: Seq[OrganizationProjectResp] = Seq.empty,
  projectData: TaskProjectData = TaskProjectData(),
  rawInput: String = "",
  tasks: Seq[BulkTask] = Seq.empty,
  awaitingRestoredData: Boolean = false
) {

  def workDays: Set[DayOfWeek] =
    projectId
      .flatMap(id => projects.find(_.id == id))
      .map(_.workDays.map(DayOfWeek.of))
      .getOrElse(WorkDayCalendar.DefaultWorkDays)

  def projectName: Option[String] =
    projectId.flatMap(id => projects.find(_.id == id)).map(project => s"${project.icon} ${project.name}".trim)

  def defaults: BulkTaskDefaults = BulkTaskDefaults.of(today, workDays, projectData.statuses)

  def lookups: BulkLookups =
    BulkLookups(projectData.statuses, projectData.labels, projectData.members, projectData.milestones, workDays)

  def draft: BulkDraft = BulkDraft(projectId, rawInput, tasks)

  /** The projects and the project's own data have both arrived; a project always has statuses, so none means not yet.
    */
  private def dataLoaded: Boolean = projects.nonEmpty && projectData.statuses.nonEmpty

  /** Chosen by the user, so it is a fresh start for the tasks: their attributes follow the new project. */
  def withProject(id: UUID): BulkState = copy(projectId = Some(id), awaitingRestoredData = false)

  /** What the tasks become when the defaults change: normally back to the defaults, which is how they follow a project
    * change, but for a restored draft only the cleanup once the data it refers to has arrived.
    */
  def afterDefaultsChange: BulkState =
    if (!awaitingRestoredData) copy(tasks = BulkTaskList.reapplyDefaults(tasks, defaults))
    else if (dataLoaded) copy(tasks = BulkTaskList.sanitize(tasks, lookups, defaults), awaitingRestoredData = false)
    else this
}
