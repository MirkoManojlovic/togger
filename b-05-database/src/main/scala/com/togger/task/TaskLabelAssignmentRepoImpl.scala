package com.togger.task

import cats.data.NonEmptyList
import cats.implicits._
import com.togger.db.Util._
import com.togger.logging.TgLogging
import com.togger.task.TaskLabelAssignmentRepoImpl.TASK_LABEL_ASSIGNMENT_TABLE_FRAG
import com.togger.task.TaskLabelRepoImpl.TASK_LABEL_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.fragment.Fragment
import doobie.util.fragments

import java.util.UUID

class TaskLabelAssignmentRepoImpl extends TaskLabelAssignmentRepo with TgLogging {

  override def insert(assignment: TaskLabelAssignment): ConnectionIO[TaskLabelAssignment] = {
    import assignment._
    logAroundInfoQuery(
      insertStatement(TASK_LABEL_ASSIGNMENT_TABLE_FRAG)(fr"""
        (task_id, task_label_id, created_at)
        values ($taskId, $taskLabelId, $createdAt)
      """).update
        .withUniqueGeneratedKeys[TaskLabelAssignment]("task_id", "task_label_id", "created_at")
    )(
      s"Inserting ${sp(assignment)}",
      assignment => s"TaskLabelAssignment inserted ${sp(assignment)}"
    )
  }

  override def deleteAllByTaskId(taskId: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(
      (deleteStatement(TASK_LABEL_ASSIGNMENT_TABLE_FRAG) ++ fr"where task_id = $taskId").update.run
    )(
      s"Deleting task label assignments with ${sp(taskId)}",
      numDeleted => s"Task label assignments deleted ${sp(numDeleted)} for ${sp(taskId)}"
    )

  override def findLabelsByTaskId(taskId: UUID): ConnectionIO[List[TaskLabel]] =
    logAroundInfoQuery(
      (selectAllStatement(TASK_LABEL_ASSIGNMENT_TABLE_FRAG) ++
        innerJoin(TASK_LABEL_TABLE_FRAG)(fr"task_label_assignment.task_label_id = task_label.id") ++
        fr"where task_label_assignment.task_id = $taskId")
        .query[(TaskLabelAssignment, TaskLabel)]
        .map(_._2)
        .to[List]
    )(
      s"Finding task labels for ${sp(taskId)}",
      labels => s"Task labels found ${sp(labels)} for ${sp(taskId)}"
    )

  override def findLabelsByTaskIds(taskIds: Seq[UUID]): ConnectionIO[Map[UUID, List[TaskLabel]]] =
    NonEmptyList.fromList(taskIds.toList) match {
      case None => Map.empty[UUID, List[TaskLabel]].pure[ConnectionIO]
      case Some(nonEmptyTaskIds) =>
        logAroundInfoQuery(
          (selectAllStatement(TASK_LABEL_ASSIGNMENT_TABLE_FRAG) ++
            innerJoin(TASK_LABEL_TABLE_FRAG)(fr"task_label_assignment.task_label_id = task_label.id") ++
            fr"where" ++ fragments.in(fr"task_label_assignment.task_id", nonEmptyTaskIds))
            .query[(TaskLabelAssignment, TaskLabel)]
            .to[List]
            .map(_.groupMap(_._1.taskId)(_._2))
        )(
          s"Finding task labels for ${sp(taskIds)}",
          labelsByTaskId => s"Task labels found ${sp(labelsByTaskId)}"
        )
    }
}

object TaskLabelAssignmentRepoImpl {
  val TASK_LABEL_ASSIGNMENT_TABLE: String = "task_label_assignment"
  val TASK_LABEL_ASSIGNMENT_TABLE_FRAG: Fragment = Fragment.const(TASK_LABEL_ASSIGNMENT_TABLE)
}
