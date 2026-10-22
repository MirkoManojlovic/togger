package com.togger.dataexportconfig

import com.togger.common.{DbPageRequest, PaginatedResponse}
import com.togger.db.Util._
import com.togger.db.{PaginationSql, TgFindPaginatedQuery}
import com.togger.dataexportconfig.DataExportConfigRepoImpl.DATA_EXPORT_CONFIG_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.fragment.Fragment

import java.time.Instant
import java.util.UUID

class DataExportConfigRepoImpl extends DataExportConfigRepo {

  private lazy val returnFields = List(
    "id",
    "project_id",
    "account_id",
    "name",
    "export_path",
    "created_at",
    "updated_at"
  )

  override def findById(id: UUID): ConnectionIO[DataExportConfig] =
    logAroundInfoQuery(
      findByIdStatementUUID(DATA_EXPORT_CONFIG_TABLE_FRAG)(id).query[DataExportConfig].unique
    )(
      s"Finding task dump config with ${sp(id)}",
      dataExportConfig => s"Task dump config found ${sp(dataExportConfig)}"
    )

  override def insert(dataExportConfig: DataExportConfig): ConnectionIO[DataExportConfig] = {
    import dataExportConfig._
    logAroundInfoQuery(
      insertStatement(DATA_EXPORT_CONFIG_TABLE_FRAG)(fr"""
        (project_id, account_id, name, export_path, created_at, updated_at)
        values ($projectId, $accountId, $name, $exportPath, $createdAt, $updatedAt)
      """).update
        .withUniqueGeneratedKeys[DataExportConfig](returnFields: _*)
    )(
      s"Inserting ${sp(dataExportConfig)}",
      dataExportConfig => s"Task dump config inserted ${sp(dataExportConfig)}"
    )
  }

  override def deleteById(id: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(
      deleteByIdStatementUUID(DATA_EXPORT_CONFIG_TABLE_FRAG)(id).update.run
    )(
      s"Deleting task dump config with ${sp(id)}",
      _ => s"Task dump config with ${sp(id)} deleted"
    )

  override def update(dataExportConfig: DataExportConfig, updatedAt: Instant): ConnectionIO[DataExportConfig] =
    logAroundInfoQuery(
      updateStatementUUID(DATA_EXPORT_CONFIG_TABLE_FRAG)(dataExportConfig.id)(fr"""
        project_id = ${dataExportConfig.projectId},
        account_id = ${dataExportConfig.accountId},
        name = ${dataExportConfig.name},
        export_path = ${dataExportConfig.exportPath},
        updated_at = $updatedAt
      """).update
        .withUniqueGeneratedKeys[DataExportConfig](returnFields: _*)
    )(
      s"Updating ${sp(dataExportConfig)}",
      dataExportConfig => s"${sp(dataExportConfig)} updated"
    )

  override def findPaginatedByProjectId(
    projectId: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[DataExportConfig]] =
    logAroundInfoQuery(
      TgFindPaginatedQuery[DataExportConfig](
        pageQuery = request =>
          (selectAllStatement(DATA_EXPORT_CONFIG_TABLE_FRAG) ++ fr"where project_id = $projectId" ++
            PaginationSql.orderBy(fr"id") ++
            PaginationSql.limitOffset(request)).query[DataExportConfig].to[List],
        countQuery = (fr"select count(*) from" ++ DATA_EXPORT_CONFIG_TABLE_FRAG ++ fr"where project_id = $projectId")
          .query[Long]
          .unique
      ).run(page)
    )(
      s"Finding task dump configs of ${sp(projectId)} with ${sp(page)}",
      taskConfigs => s"Task dump configs found ${sp(taskConfigs)} for ${sp(projectId)}"
    )

}

object DataExportConfigRepoImpl {
  val DATA_EXPORT_CONFIG_TABLE: String = "data_export_config"
  val DATA_EXPORT_CONFIG_TABLE_FRAG: Fragment = Fragment.const(DATA_EXPORT_CONFIG_TABLE)
}
