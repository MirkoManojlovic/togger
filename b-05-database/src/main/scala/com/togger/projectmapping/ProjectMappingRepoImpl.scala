package com.togger.projectmapping

import com.togger.db.Util._
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

  override def findByProjectIdDb(projectIdDb: UUID): ConnectionIO[List[ProjectMapping]] =
    logAroundInfoQuery(
      (selectAllStatement(PROJECT_MAPPING_TABLE_FRAG) ++ fr"where project_id_db = $projectIdDb")
        .query[ProjectMapping]
        .to[List]
    )(
      s"Finding project mappings for ${sp(projectIdDb)}",
      projectMappings => s"${sp(projectMappings)} found"
    )

  override def findByProjectIdDump(projectIdDump: UUID): ConnectionIO[List[ProjectMapping]] =
    logAroundInfoQuery(
      (selectAllStatement(PROJECT_MAPPING_TABLE_FRAG) ++ fr"where project_id_dump = $projectIdDump")
        .query[ProjectMapping]
        .to[List]
    )(
      s"Finding project mappings for ${sp(projectIdDump)}",
      projectMappings => s"${sp(projectMappings)} found"
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
