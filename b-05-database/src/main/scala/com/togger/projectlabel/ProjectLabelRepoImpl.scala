package com.togger.projectlabel

import com.togger.common.{DbPageRequest, PaginatedResponse}
import com.togger.db.Util._
import com.togger.db.{PaginationSql, TgFindPaginatedQuery}
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

  override def findPaginated(page: DbPageRequest): ConnectionIO[PaginatedResponse[ProjectLabel]] =
    logAroundInfoQuery(
      TgFindPaginatedQuery[ProjectLabel](
        pageQuery = request =>
          (selectAllStatement(PROJECT_LABEL_TABLE_FRAG) ++ PaginationSql.orderBy(fr"name", fr"id") ++
            PaginationSql.limitOffset(request)).query[ProjectLabel].to[List],
        countQuery = (fr"select count(*) from" ++ PROJECT_LABEL_TABLE_FRAG).query[Long].unique
      ).run(page)
    )(
      s"Finding project labels with ${sp(page)}",
      items => s"Project labels found ${sp(items)}"
    )
}

object ProjectLabelRepoImpl {
  val PROJECT_LABEL_TABLE: String = "project_label"
  val PROJECT_LABEL_TABLE_FRAG: Fragment = Fragment.const(PROJECT_LABEL_TABLE)
}
