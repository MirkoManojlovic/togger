package com.togger.milestonesteptemplate

import com.togger.common.{DbPageRequest, PaginatedResponse}
import com.togger.db.Util._
import com.togger.db.{PaginationSql, TgFindPaginatedQuery}
import com.togger.logging.TgLogging
import com.togger.milestonesteptemplate.MilestoneStepTemplateRepoImpl.MILESTONE_STEP_TEMPLATE_TABLE_FRAG
import com.togger.milestonesteptemplate.MilestoneStepTemplateVersionRepoImpl.MILESTONE_STEP_TEMPLATE_VERSION_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.fragment.Fragment

import java.util.UUID

class MilestoneStepTemplateRepoImpl extends MilestoneStepTemplateRepo with TgLogging {

  private lazy val returnFields = List("id", "project_id", "created_by", "created_at")

  override def insert(template: MilestoneStepTemplate): ConnectionIO[MilestoneStepTemplate] = {
    import template._
    logAroundInfoQuery(
      insertStatement(MILESTONE_STEP_TEMPLATE_TABLE_FRAG)(fr"""
        (project_id, created_by, created_at)
        values ($projectId, $createdBy, $createdAt)
      """).update
        .withUniqueGeneratedKeys[MilestoneStepTemplate](returnFields: _*)
    )(s"Inserting ${sp(template)}", saved => s"Milestone step template inserted ${sp(saved)}")
  }

  override def insertWithId(template: MilestoneStepTemplate): ConnectionIO[MilestoneStepTemplate] = {
    import template._
    logAroundInfoQuery(
      insertStatement(MILESTONE_STEP_TEMPLATE_TABLE_FRAG)(fr"""
        (id, project_id, created_by, created_at)
        values ($id, $projectId, $createdBy, $createdAt)
      """).update
        .withUniqueGeneratedKeys[MilestoneStepTemplate](returnFields: _*)
    )(
      s"Inserting ${sp(template)} with existing id",
      saved => s"Milestone step template inserted with existing id ${sp(saved)}"
    )
  }

  override def findById(id: UUID): ConnectionIO[MilestoneStepTemplate] =
    logAroundInfoQuery(findByIdStatementUUID(MILESTONE_STEP_TEMPLATE_TABLE_FRAG)(id).query[MilestoneStepTemplate].unique)(
      s"Finding milestone step template with ${sp(id)}",
      found => s"Milestone step template found ${sp(found)}"
    )

  override def deleteById(id: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(deleteByIdStatementUUID(MILESTONE_STEP_TEMPLATE_TABLE_FRAG)(id).update.run)(
      s"Deleting milestone step template with ${sp(id)}",
      _ => s"Milestone step template with ${sp(id)} deleted"
    )

  override def findFullById(id: UUID): ConnectionIO[MilestoneStepTemplateFull] =
    logAroundInfoQuery(
      (fullSelect ++ fr"where t.id = $id").query[(MilestoneStepTemplate, MilestoneStepTemplateVersion)].unique.map(toFull)
    )(
      s"Finding full milestone step template with ${sp(id)}",
      found => s"Full milestone step template found ${sp(found)}"
    )

  override def findPaginatedFullByProjectId(
    projectId: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[MilestoneStepTemplateFull]] =
    logAroundInfoQuery(
      TgFindPaginatedQuery[MilestoneStepTemplateFull](
        pageQuery = request =>
          (fullSelect ++ fr"where t.project_id = $projectId" ++
            PaginationSql.orderBy(fr"tv.name", fr"t.id") ++
            PaginationSql.limitOffset(request))
            .query[(MilestoneStepTemplate, MilestoneStepTemplateVersion)]
            .map(toFull)
            .to[List],
        countQuery = (countStatement(MILESTONE_STEP_TEMPLATE_TABLE_FRAG) ++ fr"where project_id = $projectId")
          .query[Long]
          .unique
      ).run(page)
    )(
      s"Finding milestone step templates of ${sp(projectId)} with ${sp(page)}",
      found => s"Milestone step templates found ${sp(found)} for ${sp(projectId)}"
    )

  private def toFull(row: (MilestoneStepTemplate, MilestoneStepTemplateVersion)): MilestoneStepTemplateFull =
    MilestoneStepTemplateFull(template = row._1, latestVersion = row._2)

  // The template and its latest version (via LATERAL).
  private def fullSelect: Fragment =
    fr"select t.id, t.project_id, t.created_by, t.created_at," ++
      fr"tv.version, tv.milestone_step_template_id, tv.name, tv.description, tv.created_at from" ++
      MILESTONE_STEP_TEMPLATE_TABLE_FRAG ++ fr"t join lateral (select * from" ++
      MILESTONE_STEP_TEMPLATE_VERSION_TABLE_FRAG ++
      fr"where milestone_step_template_id = t.id order by version desc limit 1) tv on true"
}

object MilestoneStepTemplateRepoImpl {
  val MILESTONE_STEP_TEMPLATE_TABLE: String = "milestone_step_template"
  val MILESTONE_STEP_TEMPLATE_TABLE_FRAG: Fragment = Fragment.const(MILESTONE_STEP_TEMPLATE_TABLE)
}
