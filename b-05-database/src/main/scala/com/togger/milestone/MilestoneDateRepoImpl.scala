package com.togger.milestone

import com.togger.db.Util._
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

  override def findByMilestoneId(milestoneId: UUID): ConnectionIO[List[MilestoneDate]] =
    logAroundInfoQuery(
      (selectAllStatement(MILESTONE_DATE_TABLE_FRAG) ++ fr"where milestone_id = $milestoneId").query[MilestoneDate].to[List]
    )(
      s"Finding milestone dates for ${sp(milestoneId)}",
      milestoneDates => s"Milestone dates found ${sp(milestoneDates)} for ${sp(milestoneId)}"
    )

  override def findByProjectId(projectId: UUID): ConnectionIO[List[MilestoneDate]] =
    logAroundInfoQuery(
      (fr"select milestone_date.* from" ++ MILESTONE_DATE_TABLE_FRAG ++
        innerJoin(MILESTONE_TABLE_FRAG)(fr"milestone_date.milestone_id = milestone.id") ++
        fr"where milestone.project_id = $projectId").query[MilestoneDate].to[List]
    )(
      s"Finding milestone dates for ${sp(projectId)}",
      milestoneDates => s"Milestone dates found ${sp(milestoneDates)} for ${sp(projectId)}"
    )

}

object MilestoneDateRepoImpl {
  val MILESTONE_DATE_TABLE: String = "milestone_date"
  val MILESTONE_DATE_TABLE_FRAG: Fragment = Fragment.const(MILESTONE_DATE_TABLE)
}
