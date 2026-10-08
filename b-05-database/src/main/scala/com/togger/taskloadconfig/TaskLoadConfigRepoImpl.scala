package com.togger.taskloadconfig

import com.togger.common.{DbPageRequest, PaginatedResponse}
import com.togger.db.Util._
import com.togger.db.{PaginationSql, TgFindPaginatedQuery}
import com.togger.taskloadconfig.TaskLoadConfigRepoImpl.TASK_LOAD_CONFIG_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.fragment.Fragment

import java.time.Instant
import java.util.UUID

class TaskLoadConfigRepoImpl extends TaskLoadConfigRepo {

  private lazy val returnFields = List(
    "id",
    "name",
    "dump_path",
    "project_id",
    "account_id",
    "created_at",
    "updated_at"
  )

  override def findById(id: UUID): ConnectionIO[TaskLoadConfig] =
    logAroundInfoQuery(
      findByIdStatementUUID(TASK_LOAD_CONFIG_TABLE_FRAG)(id).query[TaskLoadConfig].unique
    )(
      s"Finding task load config with ${sp(id)}",
      taskLoadConfig => s"Task load config found ${sp(taskLoadConfig)}"
    )

  override def insert(taskLoadConfig: TaskLoadConfig): ConnectionIO[TaskLoadConfig] = {
    import taskLoadConfig._
    logAroundInfoQuery(
      insertStatement(TASK_LOAD_CONFIG_TABLE_FRAG)(fr"""
        (name, dump_path, project_id, account_id, created_at, updated_at)
        values ($name, $dumpPath, $projectId, $accountId, $createdAt, $updatedAt)
      """).update
        .withUniqueGeneratedKeys[TaskLoadConfig](returnFields: _*)
    )(
      s"Inserting ${sp(taskLoadConfig)}",
      taskLoadConfig => s"Task load config inserted ${sp(taskLoadConfig)}"
    )
  }

  override def deleteById(id: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(
      deleteByIdStatementUUID(TASK_LOAD_CONFIG_TABLE_FRAG)(id).update.run
    )(
      s"Deleting task load config with ${sp(id)}",
      _ => s"Task load config with ${sp(id)} deleted"
    )

  override def update(taskLoadConfig: TaskLoadConfig, updatedAt: Instant): ConnectionIO[TaskLoadConfig] =
    logAroundInfoQuery(
      updateStatementUUID(TASK_LOAD_CONFIG_TABLE_FRAG)(taskLoadConfig.id)(fr"""
        name = ${taskLoadConfig.name},
        dump_path = ${taskLoadConfig.dumpPath},
        project_id = ${taskLoadConfig.projectId},
        account_id = ${taskLoadConfig.accountId},
        updated_at = $updatedAt
      """).update
        .withUniqueGeneratedKeys[TaskLoadConfig](returnFields: _*)
    )(
      s"Updating ${sp(taskLoadConfig)}",
      taskLoadConfig => s"${sp(taskLoadConfig)} updated"
    )

  override def findPaginatedByProjectId(
    projectId: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[TaskLoadConfig]] =
    logAroundInfoQuery(
      TgFindPaginatedQuery[TaskLoadConfig](
        pageQuery = request =>
          (selectAllStatement(TASK_LOAD_CONFIG_TABLE_FRAG) ++ fr"where project_id = $projectId" ++
            PaginationSql.orderBy(fr"id") ++
            PaginationSql.limitOffset(request)).query[TaskLoadConfig].to[List],
        countQuery = (fr"select count(*) from" ++ TASK_LOAD_CONFIG_TABLE_FRAG ++ fr"where project_id = $projectId")
          .query[Long]
          .unique
      ).run(page)
    )(
      s"Finding task load configs of ${sp(projectId)} with ${sp(page)}",
      taskConfigs => s"Task load configs found ${sp(taskConfigs)} for ${sp(projectId)}"
    )

}

object TaskLoadConfigRepoImpl {
  val TASK_LOAD_CONFIG_TABLE: String = "task_load_config"
  val TASK_LOAD_CONFIG_TABLE_FRAG: Fragment = Fragment.const(TASK_LOAD_CONFIG_TABLE)
}
