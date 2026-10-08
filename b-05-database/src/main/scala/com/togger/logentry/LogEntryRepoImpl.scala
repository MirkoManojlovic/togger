package com.togger.logentry

import com.togger.common.{DbPageRequest, PaginatedResponse}
import com.togger.db.Util._
import com.togger.db.{PaginationSql, TgFindPaginatedQuery}
import com.togger.dto.LogEntryFilter
import com.togger.logentry.LogEntryRepoImpl.LOG_ENTRY_TABLE_FRAG
import com.togger.task.TaskRepoImpl.TASK_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.{ConnectionIO, Fragment, Fragments}

import java.util.UUID

class LogEntryRepoImpl extends LogEntryRepo {

  def insert(logEntry: LogEntry): ConnectionIO[LogEntry] = {
    import logEntry._
    logAroundInfoQuery(
      insertStatement(LOG_ENTRY_TABLE_FRAG)(fr"""
        (task_id, created_by, created_at)
        values ($taskId, $createdBy, $createdAt)
      """).update
        .withUniqueGeneratedKeys[LogEntry]("id", "task_id", "created_by", "created_at")
    )(s"Inserting ${sp(logEntry)}", logEntry => s"Log entry inserted ${sp(logEntry)}")
  }

  override def insertWithId(logEntry: LogEntry): ConnectionIO[LogEntry] = {
    import logEntry._
    logAroundInfoQuery(
      insertStatement(LOG_ENTRY_TABLE_FRAG)(fr"""
        (id, task_id, created_by, created_at)
        values ($id, $taskId, $createdBy, $createdAt)
      """).update
        .withUniqueGeneratedKeys[LogEntry]("id", "task_id", "created_by", "created_at")
    )(
      s"Inserting ${sp(logEntry)} with existing id",
      logEntry => s"Log entry inserted with existing id ${sp(logEntry)}"
    )
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

  override def findByTaskId(taskId: UUID): ConnectionIO[List[LogEntry]] = {
    logAroundInfoQuery(
      (selectAllStatement(LOG_ENTRY_TABLE_FRAG) ++ fr"where task_id = $taskId").query[LogEntry].to[List]
    )(
      s"Finding log entries for ${sp(taskId)}",
      logEntries => s"Log entries found ${sp(logEntries)} for ${sp(taskId)}"
    )
  }

  override def findPaginatedByTaskId(
    taskId: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[LogEntry]] =
    logAroundInfoQuery(
      TgFindPaginatedQuery[LogEntry](
        pageQuery = request =>
          (selectAllStatement(LOG_ENTRY_TABLE_FRAG) ++ fr"where task_id = $taskId" ++
            PaginationSql.orderBy(fr"created_at desc", fr"id") ++
            PaginationSql.limitOffset(request)).query[LogEntry].to[List],
        countQuery = (fr"select count(*) from" ++ LOG_ENTRY_TABLE_FRAG ++
          fr"where task_id = $taskId").query[Long].unique
      ).run(page)
    )(
      s"Finding log entries of ${sp(taskId)} with ${sp(page)}",
      found => s"Log entries found ${sp(found)} for ${sp(taskId)}"
    )

  override def findPaginatedByProjectId(
    projectId: UUID,
    filter: LogEntryFilter,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[LogEntry]] =
    logAroundInfoQuery(
      TgFindPaginatedQuery[LogEntry](
        pageQuery = request =>
          (fr"select log_entry.*" ++ filteredEntries(projectId, filter) ++
            PaginationSql.orderBy(fr"log_entry.created_at desc", fr"log_entry.id") ++
            PaginationSql.limitOffset(request)).query[LogEntry].to[List],
        countQuery = (fr"select count(*)" ++ filteredEntries(projectId, filter)).query[Long].unique
      ).run(page)
    )(
      s"Finding log entries of ${sp(projectId)} with ${sp(filter)} and ${sp(page)}",
      entries => s"Log entries found ${sp(entries)} for ${sp(projectId)}"
    )

  // Shared by the page and the count query.
  private def filteredEntries(projectId: UUID, filter: LogEntryFilter): Fragment =
    fr"from" ++ LOG_ENTRY_TABLE_FRAG ++ innerJoin(TASK_TABLE_FRAG)(fr"log_entry.task_id = task.id") ++
      Fragments.whereAndOpt(
        Some(fr"task.project_id = $projectId"),
        filter.from.map(from => fr"log_entry.created_at >= $from"),
        filter.to.map(to => fr"log_entry.created_at < $to")
      )

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
}
