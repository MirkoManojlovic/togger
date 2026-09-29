package com.togger.task

import com.togger.db.Util._
import com.togger.logging.TgLogging
import com.togger.task.TaskLabelRepoImpl.TASK_LABEL_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.fragment.Fragment

import java.time._
import java.util.UUID

class TaskLabelRepoImpl extends TaskLabelRepo with TgLogging {

  override def insert(taskLabel: TaskLabel): ConnectionIO[TaskLabel] = {
    import taskLabel._
    logAroundInfoQuery(
      insertStatement(TASK_LABEL_TABLE_FRAG)(fr"""
        (name, description ,project_id, created_at, updated_at)
        values ($name, $description, $projectId ,$createdAt, $updatedAt)
      """).update
        .withUniqueGeneratedKeys[TaskLabel](
          "id",
          "project_id",
          "name",
          "description",
          "created_at",
          "updated_at"
        )
    )(
      s"Inserting ${sp(taskLabel)}",
      taskLabel => s"TaskLabel inserted ${sp(taskLabel)}"
    )
  }

  override def findById(id: UUID): ConnectionIO[TaskLabel] =
    logAroundInfoQuery(findByIdStatementUUID(TASK_LABEL_TABLE_FRAG)(id).query[TaskLabel].unique)(
      s"Finding taskLabel with ${sp(id)}",
      taskLabel => s"TaskLabel found ${sp(taskLabel)}"
    )

  override def deleteById(id: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(deleteByIdStatementUUID(TASK_LABEL_TABLE_FRAG)(id).update.run)(
      s"Deleting taskLabel with ${sp(id)}",
      _ => s"TaskLabel with ${sp(id)} deleted"
    )

  def update(taskLabel: TaskLabel, updatedAt: Instant): ConnectionIO[TaskLabel] = {
    logAroundInfoQuery(
      updateStatementUUID(TASK_LABEL_TABLE_FRAG)(taskLabel.id)(fr"""
           name = ${taskLabel.name},
           description = ${taskLabel.description},
           project_id = ${taskLabel.projectId},
           updated_at = $updatedAt
         """).update
        .withUniqueGeneratedKeys[TaskLabel](
          "id",
          "project_id",
          "name",
          "description",
          "created_at",
          "updated_at"
        )
    )(
      s"Updating ${sp(taskLabel)}",
      taskLabel => s"${sp(taskLabel)} updated"
    )
  }

  override def findByProjectId(projectId: UUID): ConnectionIO[List[TaskLabel]] =
    logAroundInfoQuery(
      (selectAllStatement(TASK_LABEL_TABLE_FRAG) ++ fr" where project_id= $projectId").query[TaskLabel].to[List]
    )(
      s"Finding all task labels by ${sp(projectId)}",
      labels => s"Task labels found ${sp(labels)}"
    )
}

object TaskLabelRepoImpl {
  val TASK_LABEL_TABLE: String = "task_label"
  val TASK_LABEL_TABLE_FRAG: Fragment = Fragment.const(TASK_LABEL_TABLE)
}
