package com.togger.projectlabel

import com.togger.db.Util._
import com.togger.logging.TgLogging
import com.togger.projectlabel.ProjectLabelRepoImpl.PROJECT_LABEL_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.fragment.Fragment

import java.time._
import java.util.UUID

class ProjectLabelRepoImpl extends ProjectLabelRepo with TgLogging {

  override def insert(projectLabel: ProjectLabel): ConnectionIO[ProjectLabel] = {
    import projectLabel._
    logAroundInfoQuery(
      insertStatement(PROJECT_LABEL_TABLE_FRAG)(fr"""
        (name, description, category_id, created_at, updated_at)
        values ($name, $description, $categoryId, $createdAt, $updatedAt)
      """).update
        .withUniqueGeneratedKeys[ProjectLabel]("id", "name", "description", "category_id", "created_at", "updated_at")
    )(
      s"Inserting ${sp(projectLabel)}",
      projectLabel => s"ProjectLabel inserted ${sp(projectLabel)}"
    )
  }

  override def findById(id: UUID): ConnectionIO[ProjectLabel] =
    logAroundInfoQuery(findByIdStatementUUID(PROJECT_LABEL_TABLE_FRAG)(id).query[ProjectLabel].unique)(
      s"Finding projectLabel with ${sp(id)}",
      projectLabel => s"ProjectLabel found ${sp(projectLabel)}"
    )

  override def deleteById(id: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(deleteByIdStatementUUID(PROJECT_LABEL_TABLE_FRAG)(id).update.run)(
      s"Deleting projectLabel with ${sp(id)}",
      _ => s"ProjectLabel with ${sp(id)} deleted"
    )

  override def update(projectLabel: ProjectLabel, updatedAt: Instant): ConnectionIO[ProjectLabel] =
    logAroundInfoQuery(
      updateStatementUUID(PROJECT_LABEL_TABLE_FRAG)(projectLabel.id)(fr"""
           name = ${projectLabel.name},
           description = ${projectLabel.description},
           category_id = ${projectLabel.categoryId},
           updated_at = $updatedAt
         """).update
        .withUniqueGeneratedKeys[ProjectLabel]("id", "name", "description", "category_id", "created_at", "updated_at")
    )(
      s"Updating ${sp(projectLabel)}",
      projectLabel => s"${sp(projectLabel)} updated"
    )

  override def findAll: ConnectionIO[List[ProjectLabel]] =
    logAroundInfoQuery(selectAllStatement(PROJECT_LABEL_TABLE_FRAG).query[ProjectLabel].to[List])(
      "Finding all project labels",
      projectLabels => s"Project labels found ${sp(projectLabels)}"
    )
}

object ProjectLabelRepoImpl {
  val PROJECT_LABEL_TABLE: String = "project_label"
  val PROJECT_LABEL_TABLE_FRAG: Fragment = Fragment.const(PROJECT_LABEL_TABLE)
}
