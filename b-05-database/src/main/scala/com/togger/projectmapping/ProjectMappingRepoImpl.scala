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
    "project_id_origin",
    "project_id_loaded",
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
        (project_id_origin, project_id_loaded, created_at, updated_at)
        values ($projectIdOrigin, $projectIdLoaded, $createdAt, $updatedAt)
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
        project_id_origin = ${projectMapping.projectIdOrigin},
        project_id_loaded = ${projectMapping.projectIdLoaded},
        updated_at = $updatedAt
      """).update
        .withUniqueGeneratedKeys[ProjectMapping](returnFields: _*)
    )(
      s"Updating ${sp(projectMapping)}",
      projectMapping => s"${sp(projectMapping)} updated"
    )

  override def findByProjectIdLoaded(projectIdLoaded: UUID): ConnectionIO[List[ProjectMapping]] =
    logAroundInfoQuery(
      (selectAllStatement(PROJECT_MAPPING_TABLE_FRAG) ++ fr"where project_id_loaded = $projectIdLoaded")
        .query[ProjectMapping]
        .to[List]
    )(
      s"Finding project mappings for ${sp(projectIdLoaded)}",
      projectMappings => s"${sp(projectMappings)} found"
    )

}

object ProjectMappingRepoImpl {
  val PROJECT_MAPPING_TABLE: String = "project_mapping"
  val PROJECT_MAPPING_TABLE_FRAG: Fragment = Fragment.const(PROJECT_MAPPING_TABLE)
}
