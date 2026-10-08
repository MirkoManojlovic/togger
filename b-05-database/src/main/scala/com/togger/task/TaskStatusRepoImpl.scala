package com.togger.taskStatus

import com.togger.common.{DbPageRequest, PaginatedResponse}
import com.togger.db.Util._
import com.togger.db.{PaginationSql, TgFindPaginatedQuery}
import com.togger.logging.TgLogging
import com.togger.task.{TaskStatus, TaskStatusRepo}
import com.togger.taskStatus.TaskStatusRepoImpl.TASK_STATUS_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.fragment.Fragment

import java.time._
import java.util.UUID

class TaskStatusRepoImpl extends TaskStatusRepo with TgLogging {

  override def insert(taskStatus: TaskStatus): ConnectionIO[TaskStatus] = {
    import taskStatus._
    logAroundInfoQuery(
      insertStatement(TASK_STATUS_TABLE_FRAG)(fr"""
        (name, description ,project_id, created_at, updated_at)
        values ($name, $description, $projectId ,$createdAt, $updatedAt)
      """).update
        .withUniqueGeneratedKeys[TaskStatus](
          "id",
          "project_id",
          "name",
          "description",
          "created_at",
          "updated_at"
        )
    )(
      s"Inserting ${sp(taskStatus)}",
      taskStatus => s"TaskStatus inserted ${sp(taskStatus)}"
    )
  }

  override def findById(id: UUID): ConnectionIO[TaskStatus] =
    logAroundInfoQuery(findByIdStatementUUID(TASK_STATUS_TABLE_FRAG)(id).query[TaskStatus].unique)(
      s"Finding taskStatus with ${sp(id)}",
      taskStatus => s"TaskStatus found ${sp(taskStatus)}"
    )

  override def deleteById(id: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(deleteByIdStatementUUID(TASK_STATUS_TABLE_FRAG)(id).update.run)(
      s"Deleting taskStatus with ${sp(id)}",
      _ => s"TaskStatus with ${sp(id)} deleted"
    )

  def update(taskStatus: TaskStatus, updatedAt: Instant): ConnectionIO[TaskStatus] = {
    logAroundInfoQuery(
      updateStatementUUID(TASK_STATUS_TABLE_FRAG)(taskStatus.id)(fr"""
           name = ${taskStatus.name},
           description = ${taskStatus.description},
           project_id = ${taskStatus.projectId},
           updated_at = $updatedAt
         """).update
        .withUniqueGeneratedKeys[TaskStatus](
          "id",
          "project_id",
          "name",
          "description",
          "created_at",
          "updated_at"
        )
    )(
      s"Updating ${sp(taskStatus)}",
      taskStatus => s"${sp(taskStatus)} updated"
    )
  }

  override def findPaginatedByProjectId(
    projectId: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[TaskStatus]] =
    logAroundInfoQuery(
      TgFindPaginatedQuery[TaskStatus](
        pageQuery = request =>
          (selectAllStatement(TASK_STATUS_TABLE_FRAG) ++ fr"where project_id = $projectId" ++
            PaginationSql.orderBy(fr"name", fr"id") ++
            PaginationSql.limitOffset(request)).query[TaskStatus].to[List],
        countQuery = (fr"select count(*) from" ++ TASK_STATUS_TABLE_FRAG ++ fr"where project_id = $projectId")
          .query[Long]
          .unique
      ).run(page)
    )(
      s"Finding task statuses of ${sp(projectId)} with ${sp(page)}",
      statuses => s"Task statuses found ${sp(statuses)} for ${sp(projectId)}"
    )
}

object TaskStatusRepoImpl {
  val TASK_STATUS_TABLE: String = "task_status"
  val TASK_STATUS_TABLE_FRAG: Fragment = Fragment.const(TASK_STATUS_TABLE)
}
