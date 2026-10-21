package com.togger.tasktemplate

import com.togger.common.{DbPageRequest, PaginatedResponse}
import com.togger.db.Util._
import com.togger.db.{PaginationSql, TgFindPaginatedQuery}
import com.togger.logging.TgLogging
import com.togger.tasktemplate.TaskTemplateVersionRepoImpl.TASK_TEMPLATE_VERSION_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.fragment.Fragment

import java.util.UUID

class TaskTemplateVersionRepoImpl extends TaskTemplateVersionRepo with TgLogging {

  private lazy val returnFields = List("version", "task_template_id", "name", "body", "created_at")

  override def insert(templateVersion: TaskTemplateVersion): ConnectionIO[TaskTemplateVersion] = {
    import templateVersion._
    logAroundInfoQuery(
      insertStatement(TASK_TEMPLATE_VERSION_TABLE_FRAG)(fr"""
        (task_template_id, name, body, created_at)
        values ($taskTemplateId, $name, $body, $createdAt)
      """).update
        .withUniqueGeneratedKeys[TaskTemplateVersion](returnFields: _*)
    )(s"Inserting ${sp(templateVersion)}", saved => s"Task template version inserted ${sp(saved)}")
  }

  override def findByVersion(version: UUID): ConnectionIO[TaskTemplateVersion] =
    logAroundInfoQuery(
      (selectAllStatement(TASK_TEMPLATE_VERSION_TABLE_FRAG) ++ fr"where version = $version")
        .query[TaskTemplateVersion]
        .unique
    )(
      s"Finding task template version with ${sp(version)}",
      found => s"Task template version found ${sp(found)}"
    )

  override def findLatestByTemplateId(taskTemplateId: UUID): ConnectionIO[TaskTemplateVersion] =
    logAroundInfoQuery(
      (selectAllStatement(TASK_TEMPLATE_VERSION_TABLE_FRAG) ++
        fr"where task_template_id = $taskTemplateId" ++ orderByDescLimitOne(fr"version"))
        .query[TaskTemplateVersion]
        .unique
    )(
      s"Finding latest task template version for ${sp(taskTemplateId)}",
      found => s"Latest task template version found ${sp(found)}"
    )

  override def findPaginatedByTemplateId(
    taskTemplateId: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[TaskTemplateVersion]] =
    logAroundInfoQuery(
      TgFindPaginatedQuery[TaskTemplateVersion](
        pageQuery = request =>
          (selectAllStatement(TASK_TEMPLATE_VERSION_TABLE_FRAG) ++ fr"where task_template_id = $taskTemplateId" ++
            PaginationSql.orderBy(fr"version desc") ++
            PaginationSql.limitOffset(request)).query[TaskTemplateVersion].to[List],
        countQuery = (countStatement(TASK_TEMPLATE_VERSION_TABLE_FRAG) ++
          fr"where task_template_id = $taskTemplateId").query[Long].unique
      ).run(page)
    )(
      s"Finding task template versions of ${sp(taskTemplateId)} with ${sp(page)}",
      found => s"Task template versions found ${sp(found)} for ${sp(taskTemplateId)}"
    )

  override def countByTemplateId(taskTemplateId: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(
      (countStatement(TASK_TEMPLATE_VERSION_TABLE_FRAG) ++ fr"where task_template_id = $taskTemplateId")
        .query[Int]
        .unique
    )(
      s"Counting task template versions for ${sp(taskTemplateId)}",
      count => s"Task template version count ${sp(count)} for ${sp(taskTemplateId)}"
    )
}

object TaskTemplateVersionRepoImpl {
  val TASK_TEMPLATE_VERSION_TABLE: String = "task_template_version"
  val TASK_TEMPLATE_VERSION_TABLE_FRAG: Fragment = Fragment.const(TASK_TEMPLATE_VERSION_TABLE)
}
