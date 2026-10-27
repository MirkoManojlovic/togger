package com.togger.logentry

import com.togger.common.{DbPageRequest, PaginatedResponse}
import com.togger.db.Util._
import com.togger.db.{PaginationSql, TgFindPaginatedQuery}
import com.togger.dto.LogEntryFilter
import com.togger.logentry.LogEntryRepoImpl.LOG_ENTRY_TABLE_FRAG
import com.togger.logentry.LogEntryVersionRepoImpl.{LOG_ENTRY_VERSION_TABLE_FRAG, logEntryStatusCodec}
import com.togger.task.Task
import com.togger.task.TaskRepoImpl.TASK_TABLE_FRAG
import com.togger.task.TaskVersionRepoImpl.latestVersionLateral
import com.togger.util.SplunkMacro.sp
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.{ConnectionIO, Fragment, Fragments}

import java.time.{Instant, LocalDate}
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

  override def existsByTaskIdCreatedBetween(taskId: UUID, from: Instant, to: Instant): ConnectionIO[Boolean] =
    logAroundInfoQuery(
      (fr"select exists (select 1 from" ++ LOG_ENTRY_TABLE_FRAG ++
        fr"where task_id = $taskId and created_at >= $from and created_at < $to)").query[Boolean].unique
    )(
      s"Checking log entries of ${sp(taskId)} between ${sp(from)} and ${sp(to)}",
      exists => s"Log entry exists ${sp(exists)} for ${sp(taskId)}"
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

  override def findLatestByMilestoneIdBetween(
    milestoneId: UUID,
    from: LocalDate,
    to: LocalDate,
    limit: Int
  ): ConnectionIO[List[LogEntryWithOneVersion]] =
    logAroundInfoQuery(
      (fr"""select log_entry.id, log_entry.task_id, log_entry.created_by, log_entry.created_at,
              latest_entry.version, latest_entry.log_entry_id, latest_entry.text_internal, latest_entry.text_external,
              latest_entry.status, latest_entry.created_by, latest_entry.created_at, latest_entry.updated_at
            from""" ++ LOG_ENTRY_TABLE_FRAG ++ innerJoin(TASK_TABLE_FRAG)(fr"log_entry.task_id = task.id") ++
        latestVersionLateral ++
        fr"join lateral (select * from" ++ LOG_ENTRY_VERSION_TABLE_FRAG ++
        fr"""where log_entry_id = log_entry.id order by version desc limit 1) latest_entry on true
            where tv.milestone_id = $milestoneId
              and (log_entry.created_at at time zone 'UTC')::date >= $from
              and (log_entry.created_at at time zone 'UTC')::date <= $to
            order by log_entry.created_at, log_entry.id
            limit $limit""")
        .query[(LogEntry, LogEntryVersion)]
        .to[List]
        .map(_.map { case (logEntry, version) => LogEntryWithOneVersion(logEntry, version) })
    )(
      s"Finding latest log entries of ${sp(milestoneId)} between ${sp(from)} and ${sp(to)}, limit ${sp(limit)}",
      found => s"Latest log entries found ${sp(found.size)} for ${sp(milestoneId)}"
    )

  override def findAllByProjectId(projectId: UUID): ConnectionIO[List[LogEntry]] =
    logAroundInfoQuery(
      (selectAllStatement(LOG_ENTRY_TABLE_FRAG) ++
        innerJoin(TASK_TABLE_FRAG)(fr"log_entry.task_id = task.id") ++
        fr"where task.project_id = $projectId")
        .query[(LogEntry, Task)]
        .map(_._1)
        .to[List]
    )(
      s"Finding all log entries of ${sp(projectId)}",
      logEntries => s"Log entries found ${sp(logEntries.size)} for ${sp(projectId)}"
    )

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
