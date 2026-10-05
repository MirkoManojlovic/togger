package com.togger.techstack

import com.togger.db.Util._
import com.togger.logging.TgLogging
import com.togger.techstack.TechStackCategoryRepoImpl.TECH_STACK_CATEGORY_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.fragment.Fragment

import java.time._
import java.util.UUID

class TechStackCategoryRepoImpl extends TechStackCategoryRepo with TgLogging {

  override def insert(techStackCategory: TechStackCategory): ConnectionIO[TechStackCategory] = {
    import techStackCategory._
    logAroundInfoQuery(
      insertStatement(TECH_STACK_CATEGORY_TABLE_FRAG)(fr"""
        (name, created_at, updated_at)
        values ($name, $createdAt, $updatedAt)
      """).update
        .withUniqueGeneratedKeys[TechStackCategory]("id", "name", "created_at", "updated_at")
    )(
      s"Inserting ${sp(techStackCategory)}",
      techStackCategory => s"TechStackCategory inserted ${sp(techStackCategory)}"
    )
  }

  override def findById(id: UUID): ConnectionIO[TechStackCategory] =
    logAroundInfoQuery(findByIdStatementUUID(TECH_STACK_CATEGORY_TABLE_FRAG)(id).query[TechStackCategory].unique)(
      s"Finding techStackCategory with ${sp(id)}",
      techStackCategory => s"TechStackCategory found ${sp(techStackCategory)}"
    )

  override def deleteById(id: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(deleteByIdStatementUUID(TECH_STACK_CATEGORY_TABLE_FRAG)(id).update.run)(
      s"Deleting techStackCategory with ${sp(id)}",
      _ => s"TechStackCategory with ${sp(id)} deleted"
    )

  override def update(techStackCategory: TechStackCategory, updatedAt: Instant): ConnectionIO[TechStackCategory] =
    logAroundInfoQuery(
      updateStatementUUID(TECH_STACK_CATEGORY_TABLE_FRAG)(techStackCategory.id)(fr"""
           name = ${techStackCategory.name},
           updated_at = $updatedAt
         """).update
        .withUniqueGeneratedKeys[TechStackCategory]("id", "name", "created_at", "updated_at")
    )(
      s"Updating ${sp(techStackCategory)}",
      techStackCategory => s"${sp(techStackCategory)} updated"
    )

  override def findAll: ConnectionIO[List[TechStackCategory]] =
    logAroundInfoQuery(selectAllStatement(TECH_STACK_CATEGORY_TABLE_FRAG).query[TechStackCategory].to[List])(
      "Finding all tech stack categories",
      techStackCategories => s"Tech stack categories found ${sp(techStackCategories)}"
    )
}

object TechStackCategoryRepoImpl {
  val TECH_STACK_CATEGORY_TABLE: String = "tech_stack_category"
  val TECH_STACK_CATEGORY_TABLE_FRAG: Fragment = Fragment.const(TECH_STACK_CATEGORY_TABLE)
}
