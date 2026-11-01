package com.togger.milestone

import cats.data.NonEmptyList
import cats.implicits._
import com.togger.db.Util._
import com.togger.milestone.MilestoneRepoImpl.MILESTONE_TABLE_FRAG
import com.togger.task.TaskRepoImpl.TASK_TABLE_FRAG
import com.togger.task.TaskVersionRepoImpl.latestVersionLateral
import com.togger.milestone.MilestoneStepRepoImpl.MILESTONE_STEP_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.{ConnectionIO, Fragment, Fragments}

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

  override def findAllByProjectId(projectId: UUID): ConnectionIO[List[MilestoneStep]] =
    logAroundInfoQuery(
      (fr"select milestone_step.* from" ++ MILESTONE_STEP_TABLE_FRAG ++
        innerJoin(MILESTONE_TABLE_FRAG)(fr"milestone.id = milestone_step.milestone_id") ++
        fr"where milestone.project_id = $projectId order by milestone_step.id").query[MilestoneStep].to[List]
    )(
      s"Finding the milestone steps of project ${sp(projectId)}",
      found => s"Milestone steps found ${sp(found.size)} for project ${sp(projectId)}"
    )

  override def findMilestonesWithoutShelf: ConnectionIO[List[MilestoneWithoutShelf]] =
    logAroundInfoQuery(
      sql"""
        select m.id, v.start_date, v.due_date, (select count(*) from milestone_step s where s.milestone_id = m.id)
        from milestone m
        join lateral (select * from milestone_version mv where mv.milestone_id = m.id order by mv.version desc limit 1) v
          on true
        where not exists (
          select 1 from milestone_step s
          join milestone_step_version sv on sv.milestone_step_id = s.id
          where s.milestone_id = m.id and lower(sv.name) = ${DefaultMilestoneStep.Shelf.stepName.toLowerCase}
        )
      """.query[MilestoneWithoutShelf].to[List]
    )(
      "Finding the milestones without a Shelf step",
      found => s"Milestones without a Shelf step found ${sp(found.size)}"
    )

  override def findMilestoneIdsByStepIds(stepIds: List[UUID]): ConnectionIO[Map[UUID, UUID]] =
    NonEmptyList.fromList(stepIds.distinct).fold(Map.empty[UUID, UUID].pure[ConnectionIO]) { ids =>
      logAroundInfoQuery(
        (fr"select id, milestone_id from" ++ MILESTONE_STEP_TABLE_FRAG ++ fr"where" ++ Fragments.in(fr"id", ids))
          .query[(UUID, UUID)]
          .to[List]
          .map(_.toMap)
      )(
        s"Finding the milestones of steps ${sp(stepIds)}",
        found => s"Milestones of steps found ${sp(found)}"
      )
    }

  override def findProjectIdsByStepIds(stepIds: List[UUID]): ConnectionIO[Map[UUID, UUID]] =
    NonEmptyList.fromList(stepIds.distinct).fold(Map.empty[UUID, UUID].pure[ConnectionIO]) { ids =>
      logAroundInfoQuery(
        (fr"select milestone_step.id, milestone.project_id from" ++ MILESTONE_STEP_TABLE_FRAG ++
          innerJoin(MILESTONE_TABLE_FRAG)(fr"milestone.id = milestone_step.milestone_id") ++ fr"where" ++
          Fragments.in(fr"milestone_step.id", ids)).query[(UUID, UUID)].to[List].map(_.toMap)
      )(
        s"Finding the projects of steps ${sp(stepIds)}",
        found => s"Projects of steps found ${sp(found)}"
      )
    }

  override def countTasksByStepIds(stepIds: List[UUID]): ConnectionIO[Map[UUID, MilestoneStepTaskCounts]] =
    NonEmptyList.fromList(stepIds.distinct).fold(Map.empty[UUID, MilestoneStepTaskCounts].pure[ConnectionIO]) { ids =>
      logAroundInfoQuery(
        (fr"select tv.milestone_step_id, count(*), count(tv.end_date) from" ++ TASK_TABLE_FRAG ++ latestVersionLateral ++
          fr"where" ++ Fragments.in(fr"tv.milestone_step_id", ids) ++ fr"group by tv.milestone_step_id")
          .query[(UUID, Int, Int)]
          .to[List]
          .map(_.map { case (stepId, total, done) => stepId -> MilestoneStepTaskCounts(total, done) }.toMap)
      )(
        s"Counting the tasks of steps ${sp(stepIds)}",
        found => s"Tasks of steps counted ${sp(found)}"
      )
    }

  override def countTasksOfMilestone(milestoneId: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(
      (fr"select count(*) from" ++ TASK_TABLE_FRAG ++ latestVersionLateral ++
        innerJoin(MILESTONE_STEP_TABLE_FRAG)(fr"milestone_step.id = tv.milestone_step_id") ++
        fr"where milestone_step.milestone_id = $milestoneId").query[Int].unique
    )(
      s"Counting the tasks of milestone ${sp(milestoneId)}",
      count => s"Tasks of milestone ${sp(milestoneId)} counted ${sp(count)}"
    )

}

object MilestoneStepRepoImpl {
  val MILESTONE_STEP_TABLE: String = "milestone_step"
  val MILESTONE_STEP_TABLE_FRAG: Fragment = Fragment.const(MILESTONE_STEP_TABLE)
}
