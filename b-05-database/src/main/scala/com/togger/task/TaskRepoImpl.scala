package com.togger.task

import com.togger.account.Account
import com.togger.account.AccountRepoImpl.passwordCodec
import com.togger.db.Util._
import com.togger.logging.TgLogging
import com.togger.task.TaskRepoImpl.TASK_TABLE_FRAG
import com.togger.taskStatus.TaskStatusRepoImpl.TASK_STATUS_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.fragment.Fragment

import java.time._
import java.util.UUID

class TaskRepoImpl extends TaskRepo with TgLogging {

  override def insert(task: Task): ConnectionIO[Task] = {
    import task._
    logAroundInfoQuery(
      insertStatement(TASK_TABLE_FRAG)(fr"""
        (name, project_id, description, assigned_to, created_by, task_status_id, created_at, updated_at)
        values ($name, $projectId, $description, $assignedTo, $createdBy, $taskStatusId,$createdAt, $updatedAt)
      """).update
        .withUniqueGeneratedKeys[Task](
          "id",
          "project_id",
          "name",
          "description",
          "assigned_to",
          "created_by",
          "closed_by",
          "task_status_id",
          "created_at",
          "updated_at"
        )
    )(
      s"Inserting ${sp(task)}",
      task => s"Task inserted ${sp(task)}"
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

  def update(task: Task, updatedAt: Instant): ConnectionIO[Task] = {
    logAroundInfoQuery(
      updateStatementUUID(TASK_TABLE_FRAG)(task.id)(fr"""
           name = ${task.name},
           project_id = ${task.projectId},
           description = ${task.description},
           assigned_to = ${task.assignedTo},
           closed_by = ${task.closedBy},
           task_status_id = ${task.taskStatusId},
           updated_at = $updatedAt
         """).update
        .withUniqueGeneratedKeys[Task](
          "id",
          "project_id",
          "name",
          "description",
          "assigned_to",
          "created_by",
          "closed_by",
          "task_status_id",
          "created_at",
          "updated_at"
        )
    )(
      s"Updating ${sp(task)}",
      task => s"${sp(task)} updated"
    )
  }

  override def findByProjectId(projectId: Long): ConnectionIO[Seq[TaskFull]] = {
    logAroundInfoQuery(
      (selectAllStatement(TASK_TABLE_FRAG) ++
        innerJoin(TASK_STATUS_TABLE_FRAG)(fr"task.task_status_id = task_status.id") ++
        innerJoin(fr"account as a1")(fr"task.created_by = a1.id") ++
        leftJoin(fr"account as a2")(fr"task.assigned_to = a2.id") ++
        leftJoin(fr"account as a3")(fr"task.closed_by = a3.id") ++
        fr"where task.project_id = $projectId")
        .query[(Task, TaskStatus, Account, Option[Account], Option[Account])]
        .map { case (task, status, createdBy, assignedToOpt, closedByOpt) =>
          TaskFull(
            id = task.id,
            projectId = task.projectId,
            name = task.name,
            description = task.description,
            assignedTo = assignedToOpt,
            createdBy = createdBy,
            closedBy = closedByOpt,
            taskStatus = status,
            createdAt = task.createdAt,
            updatedAt = task.updatedAt
          )
        }
        .to[Seq]
    )(
      s"Finding all tasks by ${sp(projectId)}}",
      tasks => s"Tasks found ${sp(tasks)} for ${sp(projectId)}"
    )
  }

  override def findAllTaskIdsByAssigneeId(accountId: UUID): ConnectionIO[List[UUID]] = {
    logAroundInfoQuery(
      (fr"select id from " ++ TASK_TABLE_FRAG ++ fr" where assigned_to = $accountId").query[UUID].to[List]
    )(
      s"Finding task ids for ${sp(accountId)}",
      taskIds => s"Task ids found ${sp(taskIds)}"
    )

  }

  override def findByIdWithStatus(id: UUID): ConnectionIO[(Task, TaskStatus)] =
    logAroundInfoQuery(
      (selectAllStatement(TASK_TABLE_FRAG) ++
        innerJoin(TASK_STATUS_TABLE_FRAG)(fr"task.task_status_id = task_status.id") ++
        fr"where task.id = $id")
        .query[(Task, TaskStatus)]
        .unique
    )(
      s"Finding task with task status ${sp(id)}",
      { case (task, taskStatus) =>
        s"Task ${sp(task)} with ${sp(taskStatus)} found"
      }
    )

  override def findByIdWithStatusAndAccount(id: UUID): ConnectionIO[TaskFull] = {
    logAroundInfoQuery(
      (selectAllStatement(TASK_TABLE_FRAG) ++
        innerJoin(TASK_STATUS_TABLE_FRAG)(fr"task.task_status_id = task_status.id") ++
        innerJoin(fr"account as a1")(fr"task.created_by = a1.id") ++
        leftJoin(fr"account as a2")(fr"task.assigned_to = a2.id") ++
        leftJoin(fr"account as a3")(fr"task.closed_by = a3.id") ++
        fr"where task.id = $id")
        .query[(Task, TaskStatus, Account, Option[Account], Option[Account])]
        .map { case (task, status, createdBy, assignedToOpt, closedByOpt) =>
          TaskFull(
            id = task.id,
            projectId = task.projectId,
            name = task.name,
            description = task.description,
            assignedTo = assignedToOpt,
            createdBy = createdBy,
            closedBy = closedByOpt,
            taskStatus = status,
            createdAt = task.createdAt,
            updatedAt = task.updatedAt
          )
        }
        .unique
    )(
      s"Finding task with task status, createdBy and assginedTo ${sp(id)}",
      taskFull => s"Task ${sp(taskFull)}"
    )
  }

}

object TaskRepoImpl {
  val TASK_TABLE: String = "task"
  val TASK_TABLE_FRAG: Fragment = Fragment.const(TASK_TABLE)
}
