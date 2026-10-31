package com.togger.milestonesteptemplate

import com.togger.common.{DbPageRequest, PaginatedResponse}
import com.togger.db.Util._
import com.togger.db.{PaginationSql, TgFindPaginatedQuery}
import com.togger.logging.TgLogging
import com.togger.milestonesteptemplate.MilestoneStepTemplateVersionRepoImpl.MILESTONE_STEP_TEMPLATE_VERSION_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.fragment.Fragment

import java.util.UUID

class MilestoneStepTemplateVersionRepoImpl extends MilestoneStepTemplateVersionRepo with TgLogging {

  private lazy val returnFields = List("version", "milestone_step_template_id", "name", "description", "created_at")

  override def insert(templateVersion: MilestoneStepTemplateVersion): ConnectionIO[MilestoneStepTemplateVersion] = {
    import templateVersion._
    logAroundInfoQuery(
      insertStatement(MILESTONE_STEP_TEMPLATE_VERSION_TABLE_FRAG)(fr"""
        (milestone_step_template_id, name, description, created_at)
        values ($milestoneStepTemplateId, $name, $description, $createdAt)
      """).update
        .withUniqueGeneratedKeys[MilestoneStepTemplateVersion](returnFields: _*)
    )(s"Inserting ${sp(templateVersion)}", saved => s"Milestone step template version inserted ${sp(saved)}")
  }

  override def insertWithId(
    templateVersion: MilestoneStepTemplateVersion
  ): ConnectionIO[MilestoneStepTemplateVersion] = {
    import templateVersion._
    logAroundInfoQuery(
      insertStatement(MILESTONE_STEP_TEMPLATE_VERSION_TABLE_FRAG)(fr"""
        (version, milestone_step_template_id, name, description, created_at)
        values ($version, $milestoneStepTemplateId, $name, $description, $createdAt)
      """).update
        .withUniqueGeneratedKeys[MilestoneStepTemplateVersion](returnFields: _*)
    )(
      s"Inserting ${sp(templateVersion)} with existing version",
      saved => s"Milestone step template version inserted with existing version ${sp(saved)}"
    )
  }

  override def findByVersion(version: UUID): ConnectionIO[MilestoneStepTemplateVersion] =
    logAroundInfoQuery(
      (selectAllStatement(MILESTONE_STEP_TEMPLATE_VERSION_TABLE_FRAG) ++ fr"where version = $version")
        .query[MilestoneStepTemplateVersion]
        .unique
    )(
      s"Finding milestone step template version with ${sp(version)}",
      found => s"Milestone step template version found ${sp(found)}"
    )

  override def findLatestByTemplateId(templateId: UUID): ConnectionIO[MilestoneStepTemplateVersion] =
    logAroundInfoQuery(
      (selectAllStatement(MILESTONE_STEP_TEMPLATE_VERSION_TABLE_FRAG) ++
        fr"where milestone_step_template_id = $templateId" ++ orderByDescLimitOne(fr"version"))
        .query[MilestoneStepTemplateVersion]
        .unique
    )(
      s"Finding latest milestone step template version for ${sp(templateId)}",
      found => s"Latest milestone step template version found ${sp(found)}"
    )

  override def findPaginatedByTemplateId(
    templateId: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[MilestoneStepTemplateVersion]] =
    logAroundInfoQuery(
      TgFindPaginatedQuery[MilestoneStepTemplateVersion](
        pageQuery = request =>
          (selectAllStatement(MILESTONE_STEP_TEMPLATE_VERSION_TABLE_FRAG) ++
            fr"where milestone_step_template_id = $templateId" ++
            PaginationSql.orderBy(fr"version desc") ++
            PaginationSql.limitOffset(request)).query[MilestoneStepTemplateVersion].to[List],
        countQuery = (countStatement(MILESTONE_STEP_TEMPLATE_VERSION_TABLE_FRAG) ++
          fr"where milestone_step_template_id = $templateId").query[Long].unique
      ).run(page)
    )(
      s"Finding milestone step template versions of ${sp(templateId)} with ${sp(page)}",
      found => s"Milestone step template versions found ${sp(found)} for ${sp(templateId)}"
    )
}

object MilestoneStepTemplateVersionRepoImpl {
  val MILESTONE_STEP_TEMPLATE_VERSION_TABLE: String = "milestone_step_template_version"
  val MILESTONE_STEP_TEMPLATE_VERSION_TABLE_FRAG: Fragment = Fragment.const(MILESTONE_STEP_TEMPLATE_VERSION_TABLE)
}
