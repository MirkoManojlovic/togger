package com.togger.milestone

import com.togger.common.{DbPageRequest, PaginatedResponse}
import com.togger.db.Util._
import com.togger.db.{PaginationSql, TgFindPaginatedQuery}
import com.togger.milestone.MilestoneSummaryRepoImpl.MILESTONE_SUMMARY_TABLE_FRAG
import com.togger.milestone.MilestoneRepoImpl.MILESTONE_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.{ConnectionIO, Fragment}

import java.util.UUID

class MilestoneSummaryRepoImpl extends MilestoneSummaryRepo {

  def insert(milestoneSummary: MilestoneSummary): ConnectionIO[MilestoneSummary] = {
    import milestoneSummary._
    logAroundInfoQuery(
      insertStatement(MILESTONE_SUMMARY_TABLE_FRAG)(fr"""
        (milestone_id, created_at)
        values ($milestoneId, $createdAt)
      """).update
        .withUniqueGeneratedKeys[MilestoneSummary]("id", "milestone_id", "created_at")
    )(s"Inserting ${sp(milestoneSummary)}", milestoneSummary => s"Milestone summary inserted ${sp(milestoneSummary)}")
  }

  override def insertWithId(milestoneSummary: MilestoneSummary): ConnectionIO[MilestoneSummary] = {
    import milestoneSummary._
    logAroundInfoQuery(
      insertStatement(MILESTONE_SUMMARY_TABLE_FRAG)(fr"""
        (id, milestone_id, created_at)
        values ($id, $milestoneId, $createdAt)
      """).update
        .withUniqueGeneratedKeys[MilestoneSummary]("id", "milestone_id", "created_at")
    )(
      s"Inserting ${sp(milestoneSummary)} with existing id",
      milestoneSummary => s"Milestone summary inserted with existing id ${sp(milestoneSummary)}"
    )
  }

  override def findById(id: UUID): ConnectionIO[MilestoneSummary] =
    logAroundInfoQuery(findByIdStatementUUID(MILESTONE_SUMMARY_TABLE_FRAG)(id).query[MilestoneSummary].unique)(
      s"Finding milestone summary with ${sp(id)}",
      milestoneSummary => s"Milestone summary found ${sp(milestoneSummary)}"
    )

  override def deleteById(id: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(deleteByIdStatementUUID(MILESTONE_SUMMARY_TABLE_FRAG)(id).update.run)(
      s"Deleting milestone summary with ${sp(id)}",
      numDeleted => s"Milestone summary deleted ${sp(numDeleted)}"
    )

  override def findPaginatedByMilestoneId(
    milestoneId: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[MilestoneSummary]] =
    logAroundInfoQuery(
      TgFindPaginatedQuery[MilestoneSummary](
        pageQuery = request =>
          (selectAllStatement(MILESTONE_SUMMARY_TABLE_FRAG) ++ fr"where milestone_id = $milestoneId" ++
            PaginationSql.orderBy(fr"id") ++
            PaginationSql.limitOffset(request)).query[MilestoneSummary].to[List],
        countQuery = (fr"select count(*) from" ++ MILESTONE_SUMMARY_TABLE_FRAG ++
          fr"where milestone_id = $milestoneId").query[Long].unique
      ).run(page)
    )(
      s"Finding milestone summaries of ${sp(milestoneId)} with ${sp(page)}",
      found => s"Milestone summaries found ${sp(found)} for ${sp(milestoneId)}"
    )

  override def findPaginatedByProjectId(
    projectId: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[MilestoneSummary]] =
    logAroundInfoQuery(
      TgFindPaginatedQuery[MilestoneSummary](
        pageQuery = request =>
          (fr"select milestone_summary.*" ++ projectSummaries(projectId) ++
            PaginationSql.orderBy(fr"milestone_summary.id") ++
            PaginationSql.limitOffset(request)).query[MilestoneSummary].to[List],
        countQuery = (fr"select count(*)" ++ projectSummaries(projectId)).query[Long].unique
      ).run(page)
    )(
      s"Finding milestone summaries of ${sp(projectId)} with ${sp(page)}",
      found => s"Milestone summaries found ${sp(found)} for ${sp(projectId)}"
    )

  // Shared by the page and the count query.
  private def projectSummaries(projectId: UUID): Fragment =
    fr"from" ++ MILESTONE_SUMMARY_TABLE_FRAG ++
      innerJoin(MILESTONE_TABLE_FRAG)(fr"milestone_summary.milestone_id = milestone.id") ++
      fr"where milestone.project_id = $projectId"

}

object MilestoneSummaryRepoImpl {
  val MILESTONE_SUMMARY_TABLE: String = "milestone_summary"
  val MILESTONE_SUMMARY_TABLE_FRAG: Fragment = Fragment.const(MILESTONE_SUMMARY_TABLE)
}
