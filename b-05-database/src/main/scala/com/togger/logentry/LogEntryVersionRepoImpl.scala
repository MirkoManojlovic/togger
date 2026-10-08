package com.togger.logentry

import com.togger.common.{DbPageRequest, PaginatedResponse}
import com.togger.db.Util._
import com.togger.db.{PaginationSql, TgFindPaginatedQuery}
import com.togger.logentry.LogEntryVersionRepoImpl.LOG_ENTRY_VERSION_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.{ConnectionIO, Fragment, Meta}

import java.util.UUID

class LogEntryVersionRepoImpl extends LogEntryVersionRepo {
  import LogEntryVersionRepoImpl.logEntryStatusCodec

  private lazy val returnFields = List(
    "version",
    "log_entry_id",
    "text_internal",
    "text_external",
    "status",
    "created_by",
    "created_at",
    "updated_at"
  )

  def insert(logEntryVersion: LogEntryVersion): ConnectionIO[LogEntryVersion] = {
    import logEntryVersion._
    logAroundInfoQuery(
      insertStatement(LOG_ENTRY_VERSION_TABLE_FRAG)(fr"""
        (log_entry_id, text_internal, text_external, status, created_by, created_at, updated_at)
        values ($logEntryId, $textInternal, $textExternal, $status, $createdBy, $createdAt, $updatedAt)
      """).update
        .withUniqueGeneratedKeys[LogEntryVersion](returnFields: _*)
    )(s"Inserting ${sp(logEntryVersion)}", logEntryVersion => s"Log entry version inserted ${sp(logEntryVersion)}")
  }

  override def insertWithId(logEntryVersion: LogEntryVersion): ConnectionIO[LogEntryVersion] = {
    import logEntryVersion._
    logAroundInfoQuery(
      insertStatement(LOG_ENTRY_VERSION_TABLE_FRAG)(fr"""
        (version, log_entry_id, text_internal, text_external, status, created_by, created_at, updated_at)
        values ($version, $logEntryId, $textInternal, $textExternal, $status, $createdBy, $createdAt, $updatedAt)
      """).update
        .withUniqueGeneratedKeys[LogEntryVersion](returnFields: _*)
    )(
      s"Inserting ${sp(logEntryVersion)} with existing version",
      logEntryVersion => s"Log entry version inserted with existing version ${sp(logEntryVersion)}"
    )
  }

  override def findByVersion(version: UUID): ConnectionIO[LogEntryVersion] =
    logAroundInfoQuery(
      (selectAllStatement(LOG_ENTRY_VERSION_TABLE_FRAG) ++ fr"where version = $version").query[LogEntryVersion].unique
    )(
      s"Finding log entry version with ${sp(version)}",
      logEntryVersion => s"log entry version found ${sp(logEntryVersion)}"
    )

  override def findByLogEntryId(logEntryId: UUID): ConnectionIO[List[LogEntryVersion]] =
    logAroundInfoQuery(
      (selectAllStatement(LOG_ENTRY_VERSION_TABLE_FRAG) ++
        fr"where log_entry_id = $logEntryId order by version desc").query[LogEntryVersion].to[List]
    )(
      s"Finding log entry versions for ${sp(logEntryId)}",
      logEntryVersions => s"Log entry versions found ${sp(logEntryVersions)} for ${sp(logEntryId)}"
    )

  override def findPaginatedByLogEntryId(
    logEntryId: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[LogEntryVersion]] =
    logAroundInfoQuery(
      TgFindPaginatedQuery[LogEntryVersion](
        pageQuery = request =>
          (selectAllStatement(LOG_ENTRY_VERSION_TABLE_FRAG) ++ fr"where log_entry_id = $logEntryId" ++
            PaginationSql.orderBy(fr"version desc") ++
            PaginationSql.limitOffset(request)).query[LogEntryVersion].to[List],
        countQuery = (fr"select count(*) from" ++ LOG_ENTRY_VERSION_TABLE_FRAG ++
          fr"where log_entry_id = $logEntryId").query[Long].unique
      ).run(page)
    )(
      s"Finding log entry versions of ${sp(logEntryId)} with ${sp(page)}",
      found => s"Log entry versions found ${sp(found)} for ${sp(logEntryId)}"
    )

  override def findLatestByLogEntryId(logEntryId: UUID): ConnectionIO[LogEntryVersion] =
    logAroundInfoQuery(
      (selectAllStatement(LOG_ENTRY_VERSION_TABLE_FRAG) ++
        fr"where log_entry_id = $logEntryId" ++ orderByDescLimitOne(fr"version")).query[LogEntryVersion].unique
    )(
      s"Finding latest log entry version for ${sp(logEntryId)}",
      logEntryVersion => s"Latest log entry version found ${sp(logEntryVersion)}"
    )

  override def updateContent(logEntryVersion: LogEntryVersion): ConnectionIO[LogEntryVersion] =
    logAroundInfoQuery(
      (fr"update" ++ LOG_ENTRY_VERSION_TABLE_FRAG ++ fr"""set
            text_internal = ${logEntryVersion.textInternal},
            text_external = ${logEntryVersion.textExternal},
            status = ${logEntryVersion.status},
            updated_at = ${logEntryVersion.updatedAt}
          where version = ${logEntryVersion.version}""").update
        .withUniqueGeneratedKeys[LogEntryVersion](returnFields: _*)
    )(s"Updating ${sp(logEntryVersion)}", logEntryVersion => s"${sp(logEntryVersion)} updated")

  override def deleteByVersion(version: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(
      (deleteStatement(LOG_ENTRY_VERSION_TABLE_FRAG) ++ fr"where version = $version").update.run
    )(
      s"Deleting log entry version with ${sp(version)}",
      numDeleted => s"Log entry version deleted ${sp(numDeleted)}"
    )

  override def countByLogEntryId(logEntryId: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(
      (countStatement(LOG_ENTRY_VERSION_TABLE_FRAG) ++ fr"where log_entry_id = $logEntryId").query[Int].unique
    )(
      s"Counting log entry versions for ${sp(logEntryId)}",
      count => s"Log entry version count ${sp(count)} for ${sp(logEntryId)}"
    )
}

object LogEntryVersionRepoImpl {
  val LOG_ENTRY_VERSION_TABLE: String = "log_entry_version"
  val LOG_ENTRY_VERSION_TABLE_FRAG: Fragment = Fragment.const(LOG_ENTRY_VERSION_TABLE)

  implicit val logEntryStatusCodec: Meta[LogEntryStatus] = Meta[Int].imap(LogEntryStatus.withValue)(_.value)
}
