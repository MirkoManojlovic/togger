package com.togger.techstack

import com.togger.db.Util._
import com.togger.logging.TgLogging
import com.togger.techstack.TechStackRepoImpl.TECH_STACK_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.fragment.Fragment

import java.time._
import java.util.UUID

class TechStackRepoImpl extends TechStackRepo with TgLogging {

  override def insert(techStack: TechStack): ConnectionIO[TechStack] = {
    import techStack._
    logAroundInfoQuery(
      insertStatement(TECH_STACK_TABLE_FRAG)(fr"""
        (name, description, category_id, created_at, updated_at)
        values ($name, $description, $categoryId, $createdAt, $updatedAt)
      """).update
        .withUniqueGeneratedKeys[TechStack]("id", "name", "description", "category_id", "created_at", "updated_at")
    )(
      s"Inserting ${sp(techStack)}",
      techStack => s"TechStack inserted ${sp(techStack)}"
    )
  }

  override def findById(id: UUID): ConnectionIO[TechStack] =
    logAroundInfoQuery(findByIdStatementUUID(TECH_STACK_TABLE_FRAG)(id).query[TechStack].unique)(
      s"Finding techStack with ${sp(id)}",
      techStack => s"TechStack found ${sp(techStack)}"
    )

  override def deleteById(id: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(deleteByIdStatementUUID(TECH_STACK_TABLE_FRAG)(id).update.run)(
      s"Deleting techStack with ${sp(id)}",
      _ => s"TechStack with ${sp(id)} deleted"
    )

  override def update(techStack: TechStack, updatedAt: Instant): ConnectionIO[TechStack] =
    logAroundInfoQuery(
      updateStatementUUID(TECH_STACK_TABLE_FRAG)(techStack.id)(fr"""
           name = ${techStack.name},
           description = ${techStack.description},
           category_id = ${techStack.categoryId},
           updated_at = $updatedAt
         """).update
        .withUniqueGeneratedKeys[TechStack]("id", "name", "description", "category_id", "created_at", "updated_at")
    )(
      s"Updating ${sp(techStack)}",
      techStack => s"${sp(techStack)} updated"
    )

  override def findAll: ConnectionIO[List[TechStack]] =
    logAroundInfoQuery(selectAllStatement(TECH_STACK_TABLE_FRAG).query[TechStack].to[List])(
      "Finding all tech stacks",
      techStacks => s"Tech stacks found ${sp(techStacks)}"
    )
}

object TechStackRepoImpl {
  val TECH_STACK_TABLE: String = "tech_stack"
  val TECH_STACK_TABLE_FRAG: Fragment = Fragment.const(TECH_STACK_TABLE)
}
