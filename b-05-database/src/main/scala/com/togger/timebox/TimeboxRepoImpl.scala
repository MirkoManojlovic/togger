package com.togger.timebox

import com.togger.common.{DbPageRequest, PaginatedResponse}
import com.togger.db.Util._
import com.togger.db.{PaginationSql, TgFindPaginatedQuery}
import com.togger.timebox.TimeboxRepoImpl.TIMEBOX_TABLE_FRAG
import com.togger.timebox.TimeboxVersionRepoImpl.TIMEBOX_VERSION_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.{ConnectionIO, Fragment}

import java.util.UUID

class TimeboxRepoImpl extends TimeboxRepo {

  override def insert(timebox: Timebox): ConnectionIO[Timebox] = {
    import timebox._
    logAroundInfoQuery(
      insertStatement(TIMEBOX_TABLE_FRAG)(fr"""
        (project_id, created_at)
        values ($projectId, $createdAt)
      """).update
        .withUniqueGeneratedKeys[Timebox]("id", "project_id", "created_at")
    )(s"Inserting ${sp(timebox)}", timebox => s"Timebox inserted ${sp(timebox)}")
  }

  override def findById(id: UUID): ConnectionIO[Timebox] =
    logAroundInfoQuery(findByIdStatementUUID(TIMEBOX_TABLE_FRAG)(id).query[Timebox].unique)(
      s"Finding timebox with ${sp(id)}",
      timebox => s"Timebox found ${sp(timebox)}"
    )

  override def deleteById(id: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(deleteByIdStatementUUID(TIMEBOX_TABLE_FRAG)(id).update.run)(
      s"Deleting timebox with ${sp(id)}",
      numDeleted => s"Timebox deleted ${sp(numDeleted)}"
    )

  override def findPaginatedByProjectId(
    projectId: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[Timebox]] =
    logAroundInfoQuery(
      TgFindPaginatedQuery[Timebox](
        pageQuery = request =>
          (fr"select timebox.*" ++ projectTimeboxes(projectId) ++
            PaginationSql.orderBy(fr"latest.start_date desc nulls last", fr"timebox.id") ++
            PaginationSql.limitOffset(request)).query[Timebox].to[List],
        countQuery = (fr"select count(*)" ++ projectTimeboxes(projectId)).query[Long].unique
      ).run(page)
    )(
      s"Finding timeboxes of ${sp(projectId)} with ${sp(page)}",
      timeboxes => s"Timeboxes found ${sp(timeboxes)} for ${sp(projectId)}"
    )

  // Shared by the page and the count query. The latest version is only there to order by its start date.
  private def projectTimeboxes(projectId: UUID): Fragment =
    fr"from" ++ TIMEBOX_TABLE_FRAG ++
      fr"left join lateral (select start_date from" ++ TIMEBOX_VERSION_TABLE_FRAG ++
      fr"where timebox_id = timebox.id order by version desc limit 1) latest on true" ++
      fr"where timebox.project_id = $projectId"

}

object TimeboxRepoImpl {
  val TIMEBOX_TABLE: String = "timebox"
  val TIMEBOX_TABLE_FRAG: Fragment = Fragment.const(TIMEBOX_TABLE)
}
