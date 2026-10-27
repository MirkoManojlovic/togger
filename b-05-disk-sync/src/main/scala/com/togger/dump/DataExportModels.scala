package com.togger.dump

import com.togger.account.Account
import com.togger.logentry.{LogEntry, LogEntryVersion}
import com.togger.milestone.{
  Milestone,
  MilestoneDate,
  MilestoneDateVersion,
  MilestoneSummary,
  MilestoneSummaryVersion,
  MilestoneVersion
}
import com.togger.project.Project
import com.togger.projectlabel.{ProjectLabel, ProjectLabelCategory}
import com.togger.task._
import com.togger.tasktemplate.{TaskTemplate, TaskTemplateVersion}
import com.togger.tgprompt.{TgPrompt, TgPromptVersion}
import com.togger.timebox.{Timebox, TimeboxVersion}
import io.circe.{Decoder, Encoder}

import java.time.DayOfWeek
import java.util.UUID

// `Project.workDays: Set[DayOfWeek]` has no circe instance by default (unlike LocalDate/Instant, circe has no
// built-in support for java.time.DayOfWeek) -- `ProjectDumpData` embeds `Project` directly and is
// (de)serialized via `io.circe.generic.auto._` in DataExportTreeStore/DataExportEntities, so those call sites
// need this imported into scope.
object DayOfWeekCodec {
  implicit val dayOfWeekEncoder: Encoder[DayOfWeek] = Encoder.encodeInt.contramap(_.getValue)
  implicit val dayOfWeekDecoder: Decoder[DayOfWeek] = Decoder.decodeInt.map(DayOfWeek.of)
}

case class AccountDumpData(id: UUID, firstName: String, lastName: String, email: String)

object AccountDumpData {
  def from(account: Account): AccountDumpData =
    AccountDumpData(account.id, account.firstName, account.lastName, account.email)
}

/** `project.json`: the project with the ids of its project labels and every account the tree refers to. */
case class ProjectDumpData(
  project: Project,
  projectLabelIds: List[UUID],
  referencedAccounts: List[AccountDumpData]
)

/** `members.json`: the accounts assigned to the project, resolved through [[ProjectDumpData.referencedAccounts]]. */
case class MembersDumpData(accountIds: List[UUID])

case class LinkDumpData(link: Link, versions: List[LinkVersion])

/** `tasks/<id>.json`: the task, its versions, links and the task labels assigned to it. */
case class TaskDumpData(
  task: Task,
  taskVersions: List[TaskVersion],
  links: List[LinkDumpData],
  labelIds: List[UUID]
)

/** `logentries/<id>.json` */
case class LogEntryDumpData(logEntry: LogEntry, versions: List[LogEntryVersion])

/** `projectlabels/<id>.json` */
case class ProjectLabelDumpData(label: ProjectLabel, category: ProjectLabelCategory)

case class MilestoneDateDumpData(milestoneDate: MilestoneDate, versions: List[MilestoneDateVersion])

case class MilestoneSummaryDumpData(milestoneSummary: MilestoneSummary, versions: List[MilestoneSummaryVersion])

/** `milestones/<id>.json` */
case class MilestoneDumpData(
  milestone: Milestone,
  versions: List[MilestoneVersion],
  dates: List[MilestoneDateDumpData],
  summaries: List[MilestoneSummaryDumpData]
)

object MilestoneDumpData {
  import io.circe.generic.auto._

  // Dumps written before milestone summaries existed have no `summaries` field.
  implicit val milestoneDumpDataDecoder: Decoder[MilestoneDumpData] = Decoder.instance { cursor =>
    for {
      milestone <- cursor.get[Milestone]("milestone")
      versions <- cursor.get[List[MilestoneVersion]]("versions")
      dates <- cursor.get[List[MilestoneDateDumpData]]("dates")
      summaries <- cursor.getOrElse[List[MilestoneSummaryDumpData]]("summaries")(Nil)
    } yield MilestoneDumpData(milestone, versions, dates, summaries)
  }
}

/** `tasktemplates/<id>.json` */
case class TaskTemplateDumpData(template: TaskTemplate, versions: List[TaskTemplateVersion])

case class TimeboxVersionDumpData(version: TimeboxVersion, plannedTaskVersions: List[UUID])

/** `timeboxes/<id>.json` */
case class TimeboxDumpData(timebox: Timebox, versions: List[TimeboxVersionDumpData])

/** `prompts/<id>.json`: a prompt of the exporting account with its versions. */
case class TgPromptDumpData(tgPrompt: TgPrompt, versions: List[TgPromptVersion])

sealed abstract class DataExportEntityType(val folder: String, val singleton: Boolean = false)

object DataExportEntityType {
  case object Project extends DataExportEntityType("project", singleton = true)
  case object Members extends DataExportEntityType("members", singleton = true)
  case object TaskStatus extends DataExportEntityType("taskstatuses")
  case object TaskLabel extends DataExportEntityType("tasklabels")
  case object ProjectLabel extends DataExportEntityType("projectlabels")
  case object Milestone extends DataExportEntityType("milestones")
  case object Task extends DataExportEntityType("tasks")
  case object LogEntry extends DataExportEntityType("logentries")
  case object TaskTemplate extends DataExportEntityType("tasktemplates")
  case object Timebox extends DataExportEntityType("timeboxes")
  case object TgPrompt extends DataExportEntityType("prompts")

  /** Dependency order: an entity only refers to entities listed before it. */
  lazy val all: List[DataExportEntityType] =
    List(Project, Members, TaskStatus, TaskLabel, ProjectLabel, Milestone, Task, LogEntry, TaskTemplate, Timebox, TgPrompt)

  def fromFolder(folder: String): Option[DataExportEntityType] = all.find(_.folder == folder)
}

sealed trait DataExportSyncStatus

object DataExportSyncStatus {
  case object InSync extends DataExportSyncStatus
  case object OutOfSync extends DataExportSyncStatus
  case object MissingFromExport extends DataExportSyncStatus
  case object MissingFromDb extends DataExportSyncStatus
  case object Malformed extends DataExportSyncStatus
}

case class DataExportEntityStatus(
  entityType: DataExportEntityType,
  entityId: UUID,
  entityName: Option[String],
  status: DataExportSyncStatus,
  differences: List[String]
)
case class DataExportPathStatus(
  exportPath: String,
  entityStatuses: List[DataExportEntityStatus],
  exportConfigId: Option[UUID] = None,
  name: Option[String] = None
)
case class DataExportStatusResult(pathStatuses: List[DataExportPathStatus])
