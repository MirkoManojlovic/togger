package com.togger.milestone

import com.togger.db.Util._
import com.togger.milestone.MilestoneStepRepoImpl.MILESTONE_STEP_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.{ConnectionIO, Fragment}

import java.util.UUID

class MilestoneStepRepoImpl extends MilestoneStepRepo {

  def insert(milestoneStep: MilestoneStep): ConnectionIO[MilestoneStep] = {
    import milestoneStep._
    logAroundInfoQuery(
      insertStatement(MILESTONE_STEP_TABLE_FRAG)(fr"""
        (milestone_id, created_at)
        values ($milestoneId, $createdAt)
      """).update
        .withUniqueGeneratedKeys[MilestoneStep]("id", "milestone_id", "created_at")
    )(s"Inserting ${sp(milestoneStep)}", milestoneStep => s"Milestone step inserted ${sp(milestoneStep)}")
  }

  override def insertWithId(milestoneStep: MilestoneStep): ConnectionIO[MilestoneStep] = {
    import milestoneStep._
    logAroundInfoQuery(
      insertStatement(MILESTONE_STEP_TABLE_FRAG)(fr"""
        (id, milestone_id, created_at)
        values ($id, $milestoneId, $createdAt)
      """).update
        .withUniqueGeneratedKeys[MilestoneStep]("id", "milestone_id", "created_at")
    )(
      s"Inserting ${sp(milestoneStep)} with existing id",
      milestoneStep => s"Milestone step inserted with existing id ${sp(milestoneStep)}"
    )
  }

  override def findById(id: UUID): ConnectionIO[MilestoneStep] =
    logAroundInfoQuery(findByIdStatementUUID(MILESTONE_STEP_TABLE_FRAG)(id).query[MilestoneStep].unique)(
      s"Finding milestone step with ${sp(id)}",
      milestoneStep => s"Milestone step found ${sp(milestoneStep)}"
    )

  override def deleteById(id: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(deleteByIdStatementUUID(MILESTONE_STEP_TABLE_FRAG)(id).update.run)(
      s"Deleting milestone step with ${sp(id)}",
      numDeleted => s"Milestone step deleted ${sp(numDeleted)}"
    )

  override def findAllByMilestoneId(milestoneId: UUID): ConnectionIO[List[MilestoneStep]] =
    logAroundInfoQuery(
      (selectAllStatement(MILESTONE_STEP_TABLE_FRAG) ++ fr"where milestone_id = $milestoneId order by id")
        .query[MilestoneStep]
        .to[List]
    )(
      s"Finding milestone steps of ${sp(milestoneId)}",
      found => s"Milestone steps found ${sp(found)} for ${sp(milestoneId)}"
    )

}

object MilestoneStepRepoImpl {
  val MILESTONE_STEP_TABLE: String = "milestone_step"
  val MILESTONE_STEP_TABLE_FRAG: Fragment = Fragment.const(MILESTONE_STEP_TABLE)
}
