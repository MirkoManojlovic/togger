package com.togger.logentry

import com.togger.db.Util._
import com.togger.logentry.LogEntryRepoImpl.LOG_ENTRY_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.{ConnectionIO, Fragment, Meta}

import java.time.Instant
import java.util.UUID

class LogEntryRepoImpl extends LogEntryRepo {
  import LogEntryRepoImpl.logEntryStatusCodec

  def insert(logEntry: LogEntry): ConnectionIO[LogEntry] = {
    import logEntry._
    logAroundInfoQuery(
      insertStatement(LOG_ENTRY_TABLE_FRAG)(fr"""
        (text_internal, text_external, status, task_id, created_by, created_at, updated_at)
        values ($textInternal, $textExternal, $status, $taskId, $createdBy, $createdAt, $updatedAt)
      """).update
        .withUniqueGeneratedKeys[LogEntry](
          "id",
          "text_internal",
          "text_external",
          "status",
          "task_id",
          "created_by",
          "created_at",
          "updated_at"
        )
    )(s"Inserting ${sp(logEntry)}", logEntry => s"Log entry inserted ${sp(logEntry)}")
  }

  override def findById(id: UUID): ConnectionIO[LogEntry] =
    logAroundInfoQuery(findByIdStatementUUID(LOG_ENTRY_TABLE_FRAG)(id).query[LogEntry].unique)(
      s"Finding log entry with ${sp(id)}",
      logEntry => s"log entry found ${sp(logEntry)}"
    )

  override def deleteById(id: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(deleteByIdStatementUUID(LOG_ENTRY_TABLE_FRAG)(id).update.run)(
      s"Deleting log entry with ${sp(id)}",
      logEntry => s"Log entry deleted ${sp(logEntry)}"
    )
  override def update(logEntry: LogEntry, updatedAt: Instant): ConnectionIO[LogEntry] =
    logAroundInfoQuery(
      updateStatementUUID(LOG_ENTRY_TABLE_FRAG)(logEntry.id)(fr"""
            text_internal = ${logEntry.textInternal},
            text_external = ${logEntry.textExternal},
            status = ${logEntry.status},
            task_id = ${logEntry.taskId},
            updated_at = $updatedAt
          """).update
        .withUniqueGeneratedKeys[LogEntry](
          "id",
          "text_internal",
          "text_external",
          "status",
          "task_id",
          "created_by",
          "created_at",
          "updated_at"
        )
    )(s"Updating ${sp(logEntry)}", logEntry => s"${sp(logEntry)} updated")

  override def findByTaskId(taskId: UUID): ConnectionIO[List[LogEntry]] = {
    logAroundInfoQuery(
      (selectAllStatement(LOG_ENTRY_TABLE_FRAG) ++ fr"where task_id = $taskId").query[LogEntry].to[List]
    )(
      s"Finding log entries for ${sp(taskId)}",
      logEntries => s"Log entries found ${sp(logEntries)} for ${sp(taskId)}"
    )
  }

  override def deleteByTaskId(taskId: UUID): ConnectionIO[Int] = {
    logAroundInfoQuery(
      (fr"delete from " ++ LOG_ENTRY_TABLE_FRAG ++ fr"where task_id = $taskId").update.run
    )(
      s"Deleting all entries for ${sp(taskId)}",
      numOfLogEntriesDeleted => s"Log entries deleted ${sp(numOfLogEntriesDeleted)} for ${sp(taskId)}"
    )
  }
}

object LogEntryRepoImpl {
  val LOG_ENTRY_TABLE: String = "log_entry"
  val LOG_ENTRY_TABLE_FRAG: Fragment = Fragment.const(LOG_ENTRY_TABLE)

  implicit val logEntryStatusCodec: Meta[LogEntryStatus] = Meta[Int].imap(LogEntryStatus.withValue)(_.value)

}
