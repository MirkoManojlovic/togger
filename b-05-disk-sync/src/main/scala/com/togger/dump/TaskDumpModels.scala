package com.togger.dump

import com.togger.account.Account
import com.togger.logentry.{LogEntry, LogEntryVersion}
import com.togger.project.Project
import com.togger.task._
import io.circe.{Decoder, Encoder}

import java.time.{DayOfWeek, Instant}
import java.util.UUID

// `Project.workDays: Set[DayOfWeek]` has no circe instance by default (unlike LocalDate/Instant, circe has no
// built-in support for java.time.DayOfWeek) -- `ProjectDumpManifest` embeds `Project` directly and is
// (de)serialized via `io.circe.generic.auto._` in TaskDumpDiskWriter/TaskDumpServiceTest, so those call sites
// need this imported into scope.
object DayOfWeekCodec {
  implicit val dayOfWeekEncoder: Encoder[DayOfWeek] = Encoder.encodeInt.contramap(_.getValue)
  implicit val dayOfWeekDecoder: Decoder[DayOfWeek] = Decoder.decodeInt.map(DayOfWeek.of)
}

case class LinkDumpData(link: Link, versions: List[LinkVersion])

case class LogEntryDumpData(logEntry: LogEntry, versions: List[LogEntryVersion])

case class TaskDumpData(
  task: Task,
  taskVersions: List[TaskVersion],
  links: List[LinkDumpData],
  logEntries: List[LogEntryDumpData]
)

case class AccountDumpData(id: UUID, firstName: String, lastName: String, email: String)

object AccountDumpData {
  def from(account: Account): AccountDumpData =
    AccountDumpData(account.id, account.firstName, account.lastName, account.email)
}

case class ProjectDumpManifest(
  project: Project,
  taskStatuses: List[TaskStatus],
  dumpedBy: AccountDumpData,
  referencedAccounts: List[AccountDumpData] = Nil,
  dumpedAt: Instant,
  taskCount: Int
)

sealed trait TaskDumpSyncStatus

object TaskDumpSyncStatus {
  case object InSync extends TaskDumpSyncStatus
  case object OutOfSync extends TaskDumpSyncStatus
  case object MissingFromDump extends TaskDumpSyncStatus
  case object MissingFromDb extends TaskDumpSyncStatus
  case object Malformed extends TaskDumpSyncStatus
}

case class TaskDumpTaskStatus(taskId: UUID, taskName: Option[String], status: TaskDumpSyncStatus, differences: List[String])
case class TaskDumpPathStatus(dumpPath: String, taskStatuses: List[TaskDumpTaskStatus])
case class TaskDumpStatusResult(pathStatuses: List[TaskDumpPathStatus])
