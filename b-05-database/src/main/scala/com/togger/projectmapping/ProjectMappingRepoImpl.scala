package com.togger.projectmapping

import com.togger.common.{DbPageRequest, PaginatedResponse}
import com.togger.db.Util._
import com.togger.db.{PaginationSql, TgFindPaginatedQuery}
import com.togger.projectmapping.ProjectMappingRepoImpl.PROJECT_MAPPING_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.fragment.Fragment

import java.util.UUID

class ProjectMappingRepoImpl extends ProjectMappingRepo {

  override def insert(projectMapping: ProjectMapping): ConnectionIO[ProjectMapping] = {
    import projectMapping._
    logAroundInfoQuery(
      insertStatement(PROJECT_MAPPING_TABLE_FRAG)(fr"""
        (project_id_db, project_id_dump, created_at, updated_at)
        values ($projectIdDb, $projectIdDump, $createdAt, $updatedAt)
      """).update
        .withUniqueGeneratedKeys[ProjectMapping]("project_id_db", "project_id_dump", "created_at", "updated_at")
    )(
      s"Inserting ${sp(projectMapping)}",
      projectMapping => s"Project mapping inserted ${sp(projectMapping)}"
    )
  }

  override def findPaginatedByProjectIdDb(
    projectIdDb: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[ProjectMapping]] =
    logAroundInfoQuery(
      TgFindPaginatedQuery[ProjectMapping](
        pageQuery = request =>
          (selectAllStatement(PROJECT_MAPPING_TABLE_FRAG) ++ fr"where project_id_db = $projectIdDb" ++
            PaginationSql.orderBy(fr"project_id_dump") ++
            PaginationSql.limitOffset(request)).query[ProjectMapping].to[List],
        countQuery = (fr"select count(*) from" ++ PROJECT_MAPPING_TABLE_FRAG ++
          fr"where project_id_db = $projectIdDb").query[Long].unique
      ).run(page)
    )(
      s"Finding project mappings of ${sp(projectIdDb)} with ${sp(page)}",
      mappings => s"Project mappings found ${sp(mappings)} for ${sp(projectIdDb)}"
    )

  override def findPaginatedByProjectIdDump(
    projectIdDump: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[ProjectMapping]] =
    logAroundInfoQuery(
      TgFindPaginatedQuery[ProjectMapping](
        pageQuery = request =>
          (selectAllStatement(PROJECT_MAPPING_TABLE_FRAG) ++ fr"where project_id_dump = $projectIdDump" ++
            PaginationSql.orderBy(fr"project_id_db") ++
            PaginationSql.limitOffset(request)).query[ProjectMapping].to[List],
        countQuery = (fr"select count(*) from" ++ PROJECT_MAPPING_TABLE_FRAG ++
          fr"where project_id_dump = $projectIdDump").query[Long].unique
      ).run(page)
    )(
      s"Finding project mappings of ${sp(projectIdDump)} with ${sp(page)}",
      mappings => s"Project mappings found ${sp(mappings)} for ${sp(projectIdDump)}"
    )

  override def deleteByIds(projectIdDb: UUID, projectIdDump: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(
      (deleteStatement(PROJECT_MAPPING_TABLE_FRAG) ++
        fr"where project_id_db = $projectIdDb and project_id_dump = $projectIdDump").update.run
    )(
      s"Deleting project mapping with ${sp(projectIdDb)} and ${sp(projectIdDump)}",
      _ => s"Project mapping with ${sp(projectIdDb)} and ${sp(projectIdDump)} deleted"
    )

}

object ProjectMappingRepoImpl {
  val PROJECT_MAPPING_TABLE: String = "project_mapping"
  val PROJECT_MAPPING_TABLE_FRAG: Fragment = Fragment.const(PROJECT_MAPPING_TABLE)
}
