package com.togger.projectmapping

import com.togger.db.Util._
import com.togger.projectmapping.ProjectMappingRepoImpl.PROJECT_MAPPING_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.fragment.Fragment

import java.time.Instant
import java.util.UUID

class ProjectMappingRepoImpl extends ProjectMappingRepo {

  private lazy val returnFields = List(
    "id",
    "project_id_dump",
    "project_id_db",
    "created_at",
    "updated_at"
  )

  override def findById(id: UUID): ConnectionIO[ProjectMapping] =
    logAroundInfoQuery(
      findByIdStatementUUID(PROJECT_MAPPING_TABLE_FRAG)(id).query[ProjectMapping].unique
    )(
      s"Finding project mapping with ${sp(id)}",
      projectMapping => s"Project mapping found ${sp(projectMapping)}"
    )

  override def insert(projectMapping: ProjectMapping): ConnectionIO[ProjectMapping] = {
    import projectMapping._
    logAroundInfoQuery(
      insertStatement(PROJECT_MAPPING_TABLE_FRAG)(fr"""
        (project_id_dump, project_id_db, created_at, updated_at)
        values ($projectIdDump, $projectIdDb, $createdAt, $updatedAt)
      """).update
        .withUniqueGeneratedKeys[ProjectMapping](returnFields: _*)
    )(
      s"Inserting ${sp(projectMapping)}",
      projectMapping => s"Project mapping inserted ${sp(projectMapping)}"
    )
  }

  override def deleteById(id: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(
      deleteByIdStatementUUID(PROJECT_MAPPING_TABLE_FRAG)(id).update.run
    )(
      s"Deleting project mapping with ${sp(id)}",
      _ => s"Project mapping with ${sp(id)} deleted"
    )

  override def update(projectMapping: ProjectMapping, updatedAt: Instant): ConnectionIO[ProjectMapping] =
    logAroundInfoQuery(
      updateStatementUUID(PROJECT_MAPPING_TABLE_FRAG)(projectMapping.id)(fr"""
        project_id_dump = ${projectMapping.projectIdDump},
        project_id_db = ${projectMapping.projectIdDb},
        updated_at = $updatedAt
      """).update
        .withUniqueGeneratedKeys[ProjectMapping](returnFields: _*)
    )(
      s"Updating ${sp(projectMapping)}",
      projectMapping => s"${sp(projectMapping)} updated"
    )

  override def findByProjectIdDb(projectIdDb: UUID): ConnectionIO[List[ProjectMapping]] =
    logAroundInfoQuery(
      (selectAllStatement(PROJECT_MAPPING_TABLE_FRAG) ++ fr"where project_id_db = $projectIdDb")
        .query[ProjectMapping]
        .to[List]
    )(
      s"Finding project mappings for ${sp(projectIdDb)}",
      projectMappings => s"${sp(projectMappings)} found"
    )

}

object ProjectMappingRepoImpl {
  val PROJECT_MAPPING_TABLE: String = "project_mapping"
  val PROJECT_MAPPING_TABLE_FRAG: Fragment = Fragment.const(PROJECT_MAPPING_TABLE)
}
