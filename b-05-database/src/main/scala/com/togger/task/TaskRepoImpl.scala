package com.togger.task

import com.togger.db.Util._
import com.togger.logging.TgLogging
import com.togger.task.TaskRepoImpl.TASK_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.fragment.Fragment

import java.util.UUID

class TaskRepoImpl extends TaskRepo with TgLogging {

  override def insert(task: Task): ConnectionIO[Task] = {
    import task._
    logAroundInfoQuery(
      insertStatement(TASK_TABLE_FRAG)(fr"""
        (project_id, created_by, created_at)
        values ($projectId, $createdBy, $createdAt)
      """).update
        .withUniqueGeneratedKeys[Task]("id", "project_id", "created_by", "created_at")
    )(
      s"Inserting ${sp(task)}",
      task => s"Task inserted ${sp(task)}"
    )
  }

  override def insertWithId(task: Task): ConnectionIO[Task] = {
    import task._
    logAroundInfoQuery(
      insertStatement(TASK_TABLE_FRAG)(fr"""
        (id, project_id, created_by, created_at)
        values ($id, $projectId, $createdBy, $createdAt)
      """).update
        .withUniqueGeneratedKeys[Task]("id", "project_id", "created_by", "created_at")
    )(
      s"Inserting ${sp(task)} with existing id",
      task => s"Task inserted with existing id ${sp(task)}"
    )
  }

  override def findById(id: UUID): ConnectionIO[Task] =
    logAroundInfoQuery(findByIdStatementUUID(TASK_TABLE_FRAG)(id).query[Task].unique)(
      s"Finding task with ${sp(id)}",
      task => s"Task found ${sp(task)}"
    )

  override def deleteById(id: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(deleteByIdStatementUUID(TASK_TABLE_FRAG)(id).update.run)(
      s"Deleting task with ${sp(id)}",
      _ => s"Task with ${sp(id)} deleted"
    )

}

object TaskRepoImpl {
  val TASK_TABLE: String = "task"
  val TASK_TABLE_FRAG: Fragment = Fragment.const(TASK_TABLE)
}
