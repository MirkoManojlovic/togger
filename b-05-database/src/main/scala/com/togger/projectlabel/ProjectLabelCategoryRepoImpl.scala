package com.togger.projectlabel

import com.togger.common.{DbPageRequest, PaginatedResponse}
import com.togger.db.Util._
import com.togger.db.{PaginationSql, TgFindPaginatedQuery}
import com.togger.logging.TgLogging
import com.togger.projectlabel.ProjectLabelCategoryRepoImpl.PROJECT_LABEL_CATEGORY_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.fragment.Fragment

import java.time._
import java.util.UUID

class ProjectLabelCategoryRepoImpl extends ProjectLabelCategoryRepo with TgLogging {

  override def insert(projectLabelCategory: ProjectLabelCategory): ConnectionIO[ProjectLabelCategory] = {
    import projectLabelCategory._
    logAroundInfoQuery(
      insertStatement(PROJECT_LABEL_CATEGORY_TABLE_FRAG)(fr"""
        (name, created_at, updated_at)
        values ($name, $createdAt, $updatedAt)
      """).update
        .withUniqueGeneratedKeys[ProjectLabelCategory]("id", "name", "created_at", "updated_at")
    )(
      s"Inserting ${sp(projectLabelCategory)}",
      projectLabelCategory => s"ProjectLabelCategory inserted ${sp(projectLabelCategory)}"
    )
  }

  override def findById(id: UUID): ConnectionIO[ProjectLabelCategory] =
    logAroundInfoQuery(findByIdStatementUUID(PROJECT_LABEL_CATEGORY_TABLE_FRAG)(id).query[ProjectLabelCategory].unique)(
      s"Finding projectLabelCategory with ${sp(id)}",
      projectLabelCategory => s"ProjectLabelCategory found ${sp(projectLabelCategory)}"
    )

  override def deleteById(id: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(deleteByIdStatementUUID(PROJECT_LABEL_CATEGORY_TABLE_FRAG)(id).update.run)(
      s"Deleting projectLabelCategory with ${sp(id)}",
      _ => s"ProjectLabelCategory with ${sp(id)} deleted"
    )

  override def update(projectLabelCategory: ProjectLabelCategory, updatedAt: Instant): ConnectionIO[ProjectLabelCategory] =
    logAroundInfoQuery(
      updateStatementUUID(PROJECT_LABEL_CATEGORY_TABLE_FRAG)(projectLabelCategory.id)(fr"""
           name = ${projectLabelCategory.name},
           updated_at = $updatedAt
         """).update
        .withUniqueGeneratedKeys[ProjectLabelCategory]("id", "name", "created_at", "updated_at")
    )(
      s"Updating ${sp(projectLabelCategory)}",
      projectLabelCategory => s"${sp(projectLabelCategory)} updated"
    )

  override def findPaginated(page: DbPageRequest): ConnectionIO[PaginatedResponse[ProjectLabelCategory]] =
    logAroundInfoQuery(
      TgFindPaginatedQuery[ProjectLabelCategory](
        pageQuery = request =>
          (selectAllStatement(PROJECT_LABEL_CATEGORY_TABLE_FRAG) ++ PaginationSql.orderBy(fr"name", fr"id") ++
            PaginationSql.limitOffset(request)).query[ProjectLabelCategory].to[List],
        countQuery = (fr"select count(*) from" ++ PROJECT_LABEL_CATEGORY_TABLE_FRAG).query[Long].unique
      ).run(page)
    )(
      s"Finding project label categories with ${sp(page)}",
      items => s"Project label categories found ${sp(items)}"
    )
}

object ProjectLabelCategoryRepoImpl {
  val PROJECT_LABEL_CATEGORY_TABLE: String = "project_label_category"
  val PROJECT_LABEL_CATEGORY_TABLE_FRAG: Fragment = Fragment.const(PROJECT_LABEL_CATEGORY_TABLE)
}
