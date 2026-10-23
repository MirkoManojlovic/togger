package com.togger.task

import com.togger.common.{DbPageRequest, PaginatedResponse}
import com.togger.db.Util._
import com.togger.db.{PaginationSql, TgFindPaginatedQuery}
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
        (name, description, emoji, project_id, created_at, updated_at)
        values ($name, $description, $emoji, $projectId, $createdAt, $updatedAt)
      """).update
        .withUniqueGeneratedKeys[TaskLabel](
          "id",
          "project_id",
          "name",
          "description",
          "emoji",
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
           emoji = ${taskLabel.emoji},
           project_id = ${taskLabel.projectId},
           updated_at = $updatedAt
         """).update
        .withUniqueGeneratedKeys[TaskLabel](
          "id",
          "project_id",
          "name",
          "description",
          "emoji",
          "created_at",
          "updated_at"
        )
    )(
      s"Updating ${sp(taskLabel)}",
      taskLabel => s"${sp(taskLabel)} updated"
    )
  }

  override def findPaginatedByProjectId(
    projectId: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[TaskLabel]] =
    logAroundInfoQuery(
      TgFindPaginatedQuery[TaskLabel](
        pageQuery = request =>
          (selectAllStatement(TASK_LABEL_TABLE_FRAG) ++ fr"where project_id = $projectId" ++
            PaginationSql.orderBy(fr"name", fr"id") ++
            PaginationSql.limitOffset(request)).query[TaskLabel].to[List],
        countQuery = (fr"select count(*) from" ++ TASK_LABEL_TABLE_FRAG ++ fr"where project_id = $projectId")
          .query[Long]
          .unique
      ).run(page)
    )(
      s"Finding task labels of ${sp(projectId)} with ${sp(page)}",
      labels => s"Task labels found ${sp(labels)} for ${sp(projectId)}"
    )
}

object TaskLabelRepoImpl {
  val TASK_LABEL_TABLE: String = "task_label"
  val TASK_LABEL_TABLE_FRAG: Fragment = Fragment.const(TASK_LABEL_TABLE)
}
