package com.togger.taskdumpconfig

import com.togger.db.Util._
import com.togger.taskdumpconfig.TaskDumpConfigRepoImpl.TASK_DUMP_CONFIG_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.fragment.Fragment

import java.time.Instant
import java.util.UUID

class TaskDumpConfigRepoImpl extends TaskDumpConfigRepo {

  private lazy val returnFields = List(
    "id",
    "project_id",
    "account_id",
    "name",
    "dump_path",
    "created_at",
    "updated_at"
  )

  override def findById(id: UUID): ConnectionIO[TaskDumpConfig] =
    logAroundInfoQuery(
      findByIdStatementUUID(TASK_DUMP_CONFIG_TABLE_FRAG)(id).query[TaskDumpConfig].unique
    )(
      s"Finding task dump config with ${sp(id)}",
      taskDumpConfig => s"Task dump config found ${sp(taskDumpConfig)}"
    )

  override def insert(taskDumpConfig: TaskDumpConfig): ConnectionIO[TaskDumpConfig] = {
    import taskDumpConfig._
    logAroundInfoQuery(
      insertStatement(TASK_DUMP_CONFIG_TABLE_FRAG)(fr"""
        (project_id, account_id, name, dump_path, created_at, updated_at)
        values ($projectId, $accountId, $name, $dumpPath, $createdAt, $updatedAt)
      """).update
        .withUniqueGeneratedKeys[TaskDumpConfig](returnFields: _*)
    )(
      s"Inserting ${sp(taskDumpConfig)}",
      taskDumpConfig => s"Task dump config inserted ${sp(taskDumpConfig)}"
    )
  }

  override def deleteById(id: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(
      deleteByIdStatementUUID(TASK_DUMP_CONFIG_TABLE_FRAG)(id).update.run
    )(
      s"Deleting task dump config with ${sp(id)}",
      _ => s"Task dump config with ${sp(id)} deleted"
    )

  override def update(taskDumpConfig: TaskDumpConfig, updatedAt: Instant): ConnectionIO[TaskDumpConfig] =
    logAroundInfoQuery(
      updateStatementUUID(TASK_DUMP_CONFIG_TABLE_FRAG)(taskDumpConfig.id)(fr"""
        project_id = ${taskDumpConfig.projectId},
        account_id = ${taskDumpConfig.accountId},
        name = ${taskDumpConfig.name},
        dump_path = ${taskDumpConfig.dumpPath},
        updated_at = $updatedAt
      """).update
        .withUniqueGeneratedKeys[TaskDumpConfig](returnFields: _*)
    )(
      s"Updating ${sp(taskDumpConfig)}",
      taskDumpConfig => s"${sp(taskDumpConfig)} updated"
    )

  override def findByProjectId(projectId: UUID): ConnectionIO[List[TaskDumpConfig]] =
    logAroundInfoQuery(
      (selectAllStatement(TASK_DUMP_CONFIG_TABLE_FRAG) ++ fr"where project_id = $projectId")
        .query[TaskDumpConfig]
        .to[List]
    )(
      s"Finding task dump configs for ${sp(projectId)}",
      taskDumpConfigs => s"${sp(taskDumpConfigs)} found"
    )

}

object TaskDumpConfigRepoImpl {
  val TASK_DUMP_CONFIG_TABLE: String = "task_dump_config"
  val TASK_DUMP_CONFIG_TABLE_FRAG: Fragment = Fragment.const(TASK_DUMP_CONFIG_TABLE)
}
