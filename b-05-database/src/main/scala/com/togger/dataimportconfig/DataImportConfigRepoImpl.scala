package com.togger.dataimportconfig

import com.togger.common.{DbPageRequest, PaginatedResponse}
import com.togger.db.Util._
import com.togger.db.{PaginationSql, TgFindPaginatedQuery}
import com.togger.dataimportconfig.DataImportConfigRepoImpl.DATA_IMPORT_CONFIG_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.fragment.Fragment

import java.time.Instant
import java.util.UUID

class DataImportConfigRepoImpl extends DataImportConfigRepo {

  private lazy val returnFields = List(
    "id",
    "name",
    "import_path",
    "project_id",
    "account_id",
    "created_at",
    "updated_at"
  )

  override def findById(id: UUID): ConnectionIO[DataImportConfig] =
    logAroundInfoQuery(
      findByIdStatementUUID(DATA_IMPORT_CONFIG_TABLE_FRAG)(id).query[DataImportConfig].unique
    )(
      s"Finding Import data config with ${sp(id)}",
      dataImportConfig => s"Import data config found ${sp(dataImportConfig)}"
    )

  override def insert(dataImportConfig: DataImportConfig): ConnectionIO[DataImportConfig] = {
    import dataImportConfig._
    logAroundInfoQuery(
      insertStatement(DATA_IMPORT_CONFIG_TABLE_FRAG)(fr"""
        (name, import_path, project_id, account_id, created_at, updated_at)
        values ($name, $importPath, $projectId, $accountId, $createdAt, $updatedAt)
      """).update
        .withUniqueGeneratedKeys[DataImportConfig](returnFields: _*)
    )(
      s"Inserting ${sp(dataImportConfig)}",
      dataImportConfig => s"Import data config inserted ${sp(dataImportConfig)}"
    )
  }

  override def deleteById(id: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(
      deleteByIdStatementUUID(DATA_IMPORT_CONFIG_TABLE_FRAG)(id).update.run
    )(
      s"Deleting Import data config with ${sp(id)}",
      _ => s"Import data config with ${sp(id)} deleted"
    )

  override def update(dataImportConfig: DataImportConfig, updatedAt: Instant): ConnectionIO[DataImportConfig] =
    logAroundInfoQuery(
      updateStatementUUID(DATA_IMPORT_CONFIG_TABLE_FRAG)(dataImportConfig.id)(fr"""
        name = ${dataImportConfig.name},
        import_path = ${dataImportConfig.importPath},
        project_id = ${dataImportConfig.projectId},
        account_id = ${dataImportConfig.accountId},
        updated_at = $updatedAt
      """).update
        .withUniqueGeneratedKeys[DataImportConfig](returnFields: _*)
    )(
      s"Updating ${sp(dataImportConfig)}",
      dataImportConfig => s"${sp(dataImportConfig)} updated"
    )

  override def findPaginatedByProjectId(
    projectId: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[DataImportConfig]] =
    logAroundInfoQuery(
      TgFindPaginatedQuery[DataImportConfig](
        pageQuery = request =>
          (selectAllStatement(DATA_IMPORT_CONFIG_TABLE_FRAG) ++ fr"where project_id = $projectId" ++
            PaginationSql.orderBy(fr"id") ++
            PaginationSql.limitOffset(request)).query[DataImportConfig].to[List],
        countQuery = (fr"select count(*) from" ++ DATA_IMPORT_CONFIG_TABLE_FRAG ++ fr"where project_id = $projectId")
          .query[Long]
          .unique
      ).run(page)
    )(
      s"Finding Import data configs of ${sp(projectId)} with ${sp(page)}",
      taskConfigs => s"Import data configs found ${sp(taskConfigs)} for ${sp(projectId)}"
    )

}

object DataImportConfigRepoImpl {
  val DATA_IMPORT_CONFIG_TABLE: String = "data_import_config"
  val DATA_IMPORT_CONFIG_TABLE_FRAG: Fragment = Fragment.const(DATA_IMPORT_CONFIG_TABLE)
}
