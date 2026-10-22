package com.togger.milestone

import com.togger.common.{DbPageRequest, PaginatedResponse}
import com.togger.db.Util._
import com.togger.db.{PaginationSql, TgFindPaginatedQuery}
import com.togger.milestone.MilestoneDateRepoImpl.MILESTONE_DATE_TABLE_FRAG
import com.togger.milestone.MilestoneRepoImpl.MILESTONE_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.{ConnectionIO, Fragment}

import java.util.UUID

class MilestoneDateRepoImpl extends MilestoneDateRepo {

  def insert(milestoneDate: MilestoneDate): ConnectionIO[MilestoneDate] = {
    import milestoneDate._
    logAroundInfoQuery(
      insertStatement(MILESTONE_DATE_TABLE_FRAG)(fr"""
        (milestone_id, created_at)
        values ($milestoneId, $createdAt)
      """).update
        .withUniqueGeneratedKeys[MilestoneDate]("id", "milestone_id", "created_at")
    )(s"Inserting ${sp(milestoneDate)}", milestoneDate => s"Milestone date inserted ${sp(milestoneDate)}")
  }

  override def insertWithId(milestoneDate: MilestoneDate): ConnectionIO[MilestoneDate] = {
    import milestoneDate._
    logAroundInfoQuery(
      insertStatement(MILESTONE_DATE_TABLE_FRAG)(fr"""
        (id, milestone_id, created_at)
        values ($id, $milestoneId, $createdAt)
      """).update
        .withUniqueGeneratedKeys[MilestoneDate]("id", "milestone_id", "created_at")
    )(
      s"Inserting ${sp(milestoneDate)} with existing id",
      milestoneDate => s"Milestone date inserted with existing id ${sp(milestoneDate)}"
    )
  }

  override def findById(id: UUID): ConnectionIO[MilestoneDate] =
    logAroundInfoQuery(findByIdStatementUUID(MILESTONE_DATE_TABLE_FRAG)(id).query[MilestoneDate].unique)(
      s"Finding milestone date with ${sp(id)}",
      milestoneDate => s"Milestone date found ${sp(milestoneDate)}"
    )

  override def deleteById(id: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(deleteByIdStatementUUID(MILESTONE_DATE_TABLE_FRAG)(id).update.run)(
      s"Deleting milestone date with ${sp(id)}",
      numDeleted => s"Milestone date deleted ${sp(numDeleted)}"
    )

  override def findPaginatedByMilestoneId(
    milestoneId: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[MilestoneDate]] =
    logAroundInfoQuery(
      TgFindPaginatedQuery[MilestoneDate](
        pageQuery = request =>
          (selectAllStatement(MILESTONE_DATE_TABLE_FRAG) ++ fr"where milestone_id = $milestoneId" ++
            PaginationSql.orderBy(fr"id") ++
            PaginationSql.limitOffset(request)).query[MilestoneDate].to[List],
        countQuery = (fr"select count(*) from" ++ MILESTONE_DATE_TABLE_FRAG ++
          fr"where milestone_id = $milestoneId").query[Long].unique
      ).run(page)
    )(
      s"Finding milestone dates of ${sp(milestoneId)} with ${sp(page)}",
      found => s"Milestone dates found ${sp(found)} for ${sp(milestoneId)}"
    )

  override def findPaginatedByProjectId(
    projectId: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[MilestoneDate]] =
    logAroundInfoQuery(
      TgFindPaginatedQuery[MilestoneDate](
        pageQuery = request =>
          (fr"select milestone_date.*" ++ projectDates(projectId) ++
            PaginationSql.orderBy(fr"milestone_date.id") ++
            PaginationSql.limitOffset(request)).query[MilestoneDate].to[List],
        countQuery = (fr"select count(*)" ++ projectDates(projectId)).query[Long].unique
      ).run(page)
    )(
      s"Finding milestone dates of ${sp(projectId)} with ${sp(page)}",
      found => s"Milestone dates found ${sp(found)} for ${sp(projectId)}"
    )

  // Shared by the page and the count query.
  private def projectDates(projectId: UUID): Fragment =
    fr"from" ++ MILESTONE_DATE_TABLE_FRAG ++
      innerJoin(MILESTONE_TABLE_FRAG)(fr"milestone_date.milestone_id = milestone.id") ++
      fr"where milestone.project_id = $projectId"

}

object MilestoneDateRepoImpl {
  val MILESTONE_DATE_TABLE: String = "milestone_date"
  val MILESTONE_DATE_TABLE_FRAG: Fragment = Fragment.const(MILESTONE_DATE_TABLE)
}
