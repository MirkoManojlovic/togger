package com.togger.dump

import com.togger.account.Account
import com.togger.logentry.LogEntry
import com.togger.logentry.LogEntryVersion
import com.togger.milestone.Milestone
import com.togger.milestone.MilestoneDate
import com.togger.milestone.MilestoneDateVersion
import com.togger.milestone.MilestoneStep
import com.togger.milestone.MilestoneStepVersion
import com.togger.milestone.MilestoneSummary
import com.togger.milestone.MilestoneSummaryVersion
import com.togger.milestone.MilestoneVersion
import com.togger.milestonesteptemplate.MilestoneStepTemplate
import com.togger.milestonesteptemplate.MilestoneStepTemplateVersion
import com.togger.milestonetemplate.MilestoneTemplate
import com.togger.milestonetemplate.MilestoneTemplateVersion
import com.togger.project.Project
import com.togger.projectlabel.ProjectLabel
import com.togger.projectlabel.ProjectLabelCategory
import com.togger.task._
import com.togger.tasktemplate.TaskTemplate
import com.togger.tasktemplate.TaskTemplateVersion
import com.togger.tgprompttemplate.TgPromptTemplate
import com.togger.tgprompttemplate.TgPromptTemplateVersion
import com.togger.timebox.Timebox
import com.togger.timebox.TimeboxVersion
import io.circe.Decoder
import io.circe.Encoder

import java.time.DayOfWeek
import java.time.Instant
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

/** `tasks/<id>.json`: the task, its versions, links and the task labels assigned to it. `labelsAssignedAt` is when the
  * labels were last set (a task update rewrites them all at once), so that the newer set wins in an import. Dumps
  * written before it existed have none.
  */
case class TaskDumpData(
  task: Task,
  taskVersions: List[TaskVersion],
  links: List[LinkDumpData],
  labelIds: List[UUID],
  labelsAssignedAt: Option[Instant] = None
)

/** `logentries/<id>.json` */
case class LogEntryDumpData(logEntry: LogEntry, versions: List[LogEntryVersion])

/** `projectlabels/<id>.json` */
case class ProjectLabelDumpData(label: ProjectLabel, category: ProjectLabelCategory)

case class MilestoneDateDumpData(milestoneDate: MilestoneDate, versions: List[MilestoneDateVersion])

case class MilestoneSummaryDumpData(milestoneSummary: MilestoneSummary, versions: List[MilestoneSummaryVersion])

case class MilestoneStepDumpData(milestoneStep: MilestoneStep, versions: List[MilestoneStepVersion])

/** `milestones/<id>.json` */
case class MilestoneDumpData(
  milestone: Milestone,
  versions: List[MilestoneVersion],
  dates: List[MilestoneDateDumpData],
  summaries: List[MilestoneSummaryDumpData],
  steps: List[MilestoneStepDumpData] = Nil
)

object MilestoneDumpData {
  import io.circe.generic.auto._

  // Dumps written before milestone summaries or steps existed have no `summaries` or `steps` field.
  implicit val milestoneDumpDataDecoder: Decoder[MilestoneDumpData] = Decoder.instance { cursor =>
    for {
      milestone <- cursor.get[Milestone]("milestone")
      versions <- cursor.get[List[MilestoneVersion]]("versions")
      dates <- cursor.get[List[MilestoneDateDumpData]]("dates")
      summaries <- cursor.getOrElse[List[MilestoneSummaryDumpData]]("summaries")(Nil)
      steps <- cursor.getOrElse[List[MilestoneStepDumpData]]("steps")(Nil)
    } yield MilestoneDumpData(milestone, versions, dates, summaries, steps)
  }
}

/** `tasktemplates/<id>.json` */
case class TaskTemplateDumpData(template: TaskTemplate, versions: List[TaskTemplateVersion])

/** `milestonesteptemplates/<id>.json` */
case class MilestoneStepTemplateDumpData(template: MilestoneStepTemplate, versions: List[MilestoneStepTemplateVersion])

/** A version of a milestone template with the ids of its step templates by stage; the ones of a stage run in parallel.
  */
case class MilestoneTemplateVersionDumpData(version: MilestoneTemplateVersion, stages: List[List[UUID]])

object MilestoneTemplateVersionDumpData {
  import io.circe.generic.auto._

  // Dumps written before the steps could run in parallel list the step templates in `stepTemplateIds`, one per stage.
  implicit val milestoneTemplateVersionDumpDataDecoder: Decoder[MilestoneTemplateVersionDumpData] =
    Decoder.instance { cursor =>
      for {
        version <- cursor.get[MilestoneTemplateVersion]("version")
        stages <- cursor
          .get[List[List[UUID]]]("stages")
          .orElse(
            cursor.get[List[UUID]]("stepTemplateIds").map(_.map(List(_)))
          )
      } yield MilestoneTemplateVersionDumpData(version, stages)
    }
}

/** `milestonetemplates/<id>.json` */
case class MilestoneTemplateDumpData(template: MilestoneTemplate, versions: List[MilestoneTemplateVersionDumpData])

case class TimeboxVersionDumpData(version: TimeboxVersion, plannedTaskVersions: List[UUID])

/** `timeboxes/<id>.json` */
case class TimeboxDumpData(timebox: Timebox, versions: List[TimeboxVersionDumpData])

/** `prompts/<id>.json`: a prompt of the exporting account with its versions. */
case class TgPromptTemplateDumpData(tgPromptTemplate: TgPromptTemplate, versions: List[TgPromptTemplateVersion])

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
  case object MilestoneStepTemplate extends DataExportEntityType("milestonesteptemplates")
  case object MilestoneTemplate extends DataExportEntityType("milestonetemplates")
  case object Timebox extends DataExportEntityType("timeboxes")
  case object TgPromptTemplate extends DataExportEntityType("prompts")

  /** Dependency order: an entity only refers to entities listed before it. */
  lazy val all: List[DataExportEntityType] =
    List(
      Project,
      Members,
      TaskStatus,
      TaskLabel,
      ProjectLabel,
      Milestone,
      Task,
      LogEntry,
      TaskTemplate,
      MilestoneStepTemplate,
      MilestoneTemplate,
      Timebox,
      TgPromptTemplate
    )

  def fromFolder(folder: String): Option[DataExportEntityType] = all.find(_.folder == folder)
}

sealed trait DataExportSyncStatus

object DataExportSyncStatus {
  case object InSync extends DataExportSyncStatus
  case object OutOfSync extends DataExportSyncStatus
  case object MissingFromExport extends DataExportSyncStatus
  case object MissingFromDb extends DataExportSyncStatus

  /** The disk deleted the entity, which the database still has unchanged: an import deletes it. */
  case object PendingDelete extends DataExportSyncStatus

  /** The database deleted the entity, which the disk still has unchanged: an export removes it from the tree. */
  case object Deleted extends DataExportSyncStatus
  case object Malformed extends DataExportSyncStatus
}

case class DataExportEntityStatus(
  entityType: DataExportEntityType,
  entityId: UUID,
  entityName: Option[String],
  status: DataExportSyncStatus,
  differences: List[String],
  fieldDiffs: List[ExportFieldDiff] = Nil
)
case class DataExportPathStatus(
  exportPath: String,
  entityStatuses: List[DataExportEntityStatus],
  exportConfigId: Option[UUID] = None,
  name: Option[String] = None
)
case class DataExportStatusResult(pathStatuses: List[DataExportPathStatus])
