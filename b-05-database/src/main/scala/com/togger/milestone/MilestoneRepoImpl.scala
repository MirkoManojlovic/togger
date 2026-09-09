package com.togger.milestone

import com.togger.db.Util._
import com.togger.milestone.MilestoneRepoImpl.MILESTONE_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.{ConnectionIO, Fragment}

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

  override def findByProjectId(projectId: UUID): ConnectionIO[List[Milestone]] =
    logAroundInfoQuery(
      (selectAllStatement(MILESTONE_TABLE_FRAG) ++ fr"where project_id = $projectId").query[Milestone].to[List]
    )(
      s"Finding milestones for ${sp(projectId)}",
      milestones => s"Milestones found ${sp(milestones)} for ${sp(projectId)}"
    )

}

object MilestoneRepoImpl {
  val MILESTONE_TABLE: String = "milestone"
  val MILESTONE_TABLE_FRAG: Fragment = Fragment.const(MILESTONE_TABLE)
}
