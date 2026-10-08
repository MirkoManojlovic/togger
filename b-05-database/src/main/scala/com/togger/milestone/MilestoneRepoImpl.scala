package com.togger.milestone

import cats.data.NonEmptyList
import com.togger.common.{DbPageRequest, PaginatedResponse}
import com.togger.db.Util._
import com.togger.db.{PaginationSql, TgFindPaginatedQuery}
import com.togger.dto.MilestoneFilter
import com.togger.milestone.MilestoneRepoImpl.MILESTONE_TABLE_FRAG
import com.togger.milestone.MilestoneVersionRepoImpl.{MILESTONE_VERSION_TABLE_FRAG, milestoneStatusCodec}
import com.togger.util.SplunkMacro.sp
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.{ConnectionIO, Fragment, Fragments}

import java.util.UUID

class MilestoneRepoImpl extends MilestoneRepo {

  def insert(milestone: Milestone): ConnectionIO[Milestone] = {
    import milestone._
    logAroundInfoQuery(
      insertStatement(MILESTONE_TABLE_FRAG)(fr"""
        (project_id, created_at)
        values ($projectId, $createdAt)
      """).update
        .withUniqueGeneratedKeys[Milestone]("id", "project_id", "created_at")
    )(s"Inserting ${sp(milestone)}", milestone => s"Milestone inserted ${sp(milestone)}")
  }

  override def findById(id: UUID): ConnectionIO[Milestone] =
    logAroundInfoQuery(findByIdStatementUUID(MILESTONE_TABLE_FRAG)(id).query[Milestone].unique)(
      s"Finding milestone with ${sp(id)}",
      milestone => s"Milestone found ${sp(milestone)}"
    )

  override def deleteById(id: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(deleteByIdStatementUUID(MILESTONE_TABLE_FRAG)(id).update.run)(
      s"Deleting milestone with ${sp(id)}",
      numDeleted => s"Milestone deleted ${sp(numDeleted)}"
    )

  override def findPaginatedByProjectId(
    projectId: UUID,
    filter: MilestoneFilter,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[Milestone]] =
    logAroundInfoQuery(
      TgFindPaginatedQuery[Milestone](
        pageQuery = request =>
          (fr"select milestone.*" ++ filteredMilestones(projectId, filter) ++
            PaginationSql.orderBy(fr"latest.due_date", fr"milestone.id") ++
            PaginationSql.limitOffset(request)).query[Milestone].to[List],
        countQuery = (fr"select count(*)" ++ filteredMilestones(projectId, filter)).query[Long].unique
      ).run(page)
    )(
      s"Finding milestones of ${sp(projectId)} with ${sp(filter)} and ${sp(page)}",
      milestones => s"Milestones found ${sp(milestones)} for ${sp(projectId)}"
    )

  // Shared by the page and the count query. Every milestone has a version, so the lateral join keeps one row each.
  private def filteredMilestones(projectId: UUID, filter: MilestoneFilter): Fragment =
    fr"from" ++ MILESTONE_TABLE_FRAG ++
      fr"join lateral (select * from" ++ MILESTONE_VERSION_TABLE_FRAG ++
      fr"where milestone_id = milestone.id order by version desc limit 1) latest on true" ++
      Fragments.whereAndOpt(
        Some(fr"milestone.project_id = $projectId"),
        NonEmptyList.fromList(filter.statuses).map(Fragments.in(fr"latest.status", _)),
        filter.search.map(_.trim).filter(_.nonEmpty).map(matchesText)
      )

  private def matchesText(term: String): Fragment = {
    val pattern = PaginationSql.containsPattern(term)
    fr"(latest.name ilike $pattern or latest.description ilike $pattern)"
  }

}

object MilestoneRepoImpl {
  val MILESTONE_TABLE: String = "milestone"
  val MILESTONE_TABLE_FRAG: Fragment = Fragment.const(MILESTONE_TABLE)
}
