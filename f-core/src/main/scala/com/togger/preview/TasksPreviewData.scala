package com.togger.preview

import com.togger.dto.{LinkVersionDto, LogEntryVersionDto, SearchAccountDto, TaskVersionDto}
import io.circe.generic.auto._

import java.time.{Instant, LocalDate}
import java.util.UUID

case class PreviewTask(id: UUID, projectId: UUID, createdBy: UUID, createdAt: Instant, archived: Boolean, labelIds: List[UUID])

case class PreviewLink(id: UUID, taskId: UUID, createdAt: Instant)

case class PreviewLogEntry(id: UUID, taskId: UUID, createdBy: UUID, createdAt: Instant)

case class PreviewTimebox(id: UUID, projectId: UUID, createdAt: Instant)

case class PreviewTimeboxVersion(
  version: UUID,
  timeboxId: UUID,
  startDate: LocalDate,
  endDate: LocalDate,
  description: Option[String],
  createdAt: Instant,
  changeComment: String,
  taskVersionIds: List[UUID]
)

/** Tasks, links, log entries and timeboxes with their versions. Versions are stored oldest first, the last one of an
  * entity is its current content. A timebox version refers to the task versions it plans.
  */
class TasksPreviewData(
  db: PreviewDb,
  projects: ProjectsPreviewData,
  taskConfig: TaskConfigPreviewData,
  milestones: MilestonesPreviewData,
  accounts: AccountsPreviewData,
  taskStats: TaskStatsPreviewData
) {

  val tasks: PreviewCollection[PreviewTask] = db.collection[PreviewTask](TasksPreviewData.TasksName, _.id)

  val taskVersions: PreviewCollection[TaskVersionDto] =
    db.collection[TaskVersionDto](TasksPreviewData.TaskVersionsName, _.version)

  val links: PreviewCollection[PreviewLink] = db.collection[PreviewLink](TasksPreviewData.LinksName, _.id)

  val linkVersions: PreviewCollection[LinkVersionDto] =
    db.collection[LinkVersionDto](TasksPreviewData.LinkVersionsName, _.version)

  val logEntries: PreviewCollection[PreviewLogEntry] =
    db.collection[PreviewLogEntry](TasksPreviewData.LogEntriesName, _.id)

  val logEntryVersions: PreviewCollection[LogEntryVersionDto] =
    db.collection[LogEntryVersionDto](TasksPreviewData.LogEntryVersionsName, _.version)

  val timeboxes: PreviewCollection[PreviewTimebox] =
    db.collection[PreviewTimebox](TasksPreviewData.TimeboxesName, _.id)

  val timeboxVersions: PreviewCollection[PreviewTimeboxVersion] =
    db.collection[PreviewTimeboxVersion](TasksPreviewData.TimeboxVersionsName, _.version)

  taskStats.source = new TaskStatsSource {
    override def statusInUse(statusId: UUID): Boolean = taskVersions.all.exists(_.taskStatusId == statusId)

    override def activityOf(milestoneId: UUID, from: LocalDate, to: LocalDate): List[MilestoneActivity] =
      tasks.all.flatMap { task =>
        latestTaskVersion(task.id).filter(_.milestone.contains(milestoneId)).flatMap { version =>
          val texts = logEntriesOf(task.id)
            .filter(entry => !dayOf(entry.createdAt).isBefore(from) && !dayOf(entry.createdAt).isAfter(to))
            .flatMap(entry => latestLogEntryVersion(entry.id))
            .map(entry => if (entry.textExternal.trim.nonEmpty) entry.textExternal else entry.textInternal)
          Option.when(texts.nonEmpty)(
            MilestoneActivity(version.name, taskConfig.statuses.find(version.taskStatusId).fold("")(_.name), texts)
          )
        }
      }
  }

  milestones.onMilestoneDeleted(milestoneId =>
    taskVersions.all
      .filter(_.milestone.contains(milestoneId))
      .foreach(version => taskVersions.upsert(version.copy(milestone = None)))
  )

  taskConfig.onTaskLabelDeleted(labelId =>
    tasks.all.filter(_.labelIds.contains(labelId)).foreach(task => tasks.upsert(task.copy(labelIds = task.labelIds.filterNot(_ == labelId))))
  )

  projects.onProjectDeleted { projectId =>
    tasks.filter(_.projectId == projectId).foreach(task => deleteTask(task.id))
    timeboxes.filter(_.projectId == projectId).foreach(timebox => deleteTimebox(timebox.id))
  }

  def dayOf(instant: Instant): LocalDate = instant.atZone(java.time.ZoneOffset.UTC).toLocalDate

  def versionsOfTask(taskId: UUID): List[TaskVersionDto] = taskVersions.filter(_.taskId == taskId)

  def latestTaskVersion(taskId: UUID): Option[TaskVersionDto] = versionsOfTask(taskId).lastOption

  def linksOf(taskId: UUID): List[PreviewLink] = links.filter(_.taskId == taskId)

  def latestLinkVersion(linkId: UUID): Option[LinkVersionDto] = linkVersions.filter(_.linkId == linkId).lastOption

  def logEntriesOf(taskId: UUID): List[PreviewLogEntry] = logEntries.filter(_.taskId == taskId)

  def versionsOfLogEntry(logEntryId: UUID): List[LogEntryVersionDto] =
    logEntryVersions.filter(_.logEntryId == logEntryId)

  def latestLogEntryVersion(logEntryId: UUID): Option[LogEntryVersionDto] = versionsOfLogEntry(logEntryId).lastOption

  def latestTimeboxVersion(timeboxId: UUID): Option[PreviewTimeboxVersion] =
    timeboxVersions.filter(_.timeboxId == timeboxId).lastOption

  def accountName(accountId: UUID): String =
    accounts.accounts.find(accountId).fold("")(account => s"${account.firstName} ${account.lastName}")

  def searchAccount(accountId: UUID): SearchAccountDto =
    accounts.accounts
      .find(accountId)
      .fold(SearchAccountDto(accountId, "Unknown", "", "", PreviewAccountTypes.BusinessId))(_.toSearchDto)

  def deleteTask(taskId: UUID): Boolean = {
    val versionIds = versionsOfTask(taskId).map(_.version).toSet
    removeFromTimeboxes(versionIds)
    linksOf(taskId).foreach(link => linkVersions.deleteWhere(_.linkId == link.id))
    links.deleteWhere(_.taskId == taskId)
    logEntriesOf(taskId).foreach(entry => logEntryVersions.deleteWhere(_.logEntryId == entry.id))
    logEntries.deleteWhere(_.taskId == taskId)
    taskVersions.deleteWhere(_.taskId == taskId)
    tasks.delete(taskId)
  }

  def deleteTaskVersion(version: UUID): Unit = {
    removeFromTimeboxes(Set(version))
    taskVersions.delete(version)
  }

  def deleteTimebox(timeboxId: UUID): Boolean = {
    timeboxVersions.deleteWhere(_.timeboxId == timeboxId)
    timeboxes.delete(timeboxId)
  }

  private def removeFromTimeboxes(versionIds: Set[UUID]): Unit =
    timeboxVersions.all
      .filter(_.taskVersionIds.exists(versionIds.contains))
      .foreach(version => timeboxVersions.upsert(version.copy(taskVersionIds = version.taskVersionIds.filterNot(versionIds.contains))))
}

object TasksPreviewData {
  val TasksName = "tasks"
  val TaskVersionsName = "task-versions"
  val LinksName = "task-links"
  val LinkVersionsName = "task-link-versions"
  val LogEntriesName = "log-entries"
  val LogEntryVersionsName = "log-entry-versions"
  val TimeboxesName = "timeboxes"
  val TimeboxVersionsName = "timebox-versions"
}
