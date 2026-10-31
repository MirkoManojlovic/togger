package com.togger.milestonetemplate

import cats.implicits._
import com.togger.common.{DbPageRequest, PaginatedResponse}
import com.togger.db.Util._
import com.togger.db.{PaginationSql, TgFindPaginatedQuery}
import com.togger.logging.TgLogging
import com.togger.milestonesteptemplate.MilestoneStepTemplateRepoImpl.MILESTONE_STEP_TEMPLATE_TABLE_FRAG
import com.togger.milestonetemplate.MilestoneTemplateRepoImpl.MILESTONE_TEMPLATE_TABLE_FRAG
import com.togger.milestonetemplate.MilestoneTemplateVersionRepoImpl.{
  MILESTONE_TEMPLATE_VERSION_STEP_TABLE_FRAG,
  MILESTONE_TEMPLATE_VERSION_TABLE_FRAG
}
import com.togger.util.SplunkMacro.sp
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.fragment.Fragment

import java.util.UUID

class MilestoneTemplateRepoImpl(versionRepo: MilestoneTemplateVersionRepo) extends MilestoneTemplateRepo with TgLogging {

  private lazy val returnFields = List("id", "project_id", "created_by", "created_at")

  override def insert(template: MilestoneTemplate): ConnectionIO[MilestoneTemplate] = {
    import template._
    logAroundInfoQuery(
      insertStatement(MILESTONE_TEMPLATE_TABLE_FRAG)(fr"""
        (project_id, created_by, created_at)
        values ($projectId, $createdBy, $createdAt)
      """).update
        .withUniqueGeneratedKeys[MilestoneTemplate](returnFields: _*)
    )(s"Inserting ${sp(template)}", saved => s"Milestone template inserted ${sp(saved)}")
  }

  override def insertWithId(template: MilestoneTemplate): ConnectionIO[MilestoneTemplate] = {
    import template._
    logAroundInfoQuery(
      insertStatement(MILESTONE_TEMPLATE_TABLE_FRAG)(fr"""
        (id, project_id, created_by, created_at)
        values ($id, $projectId, $createdBy, $createdAt)
      """).update
        .withUniqueGeneratedKeys[MilestoneTemplate](returnFields: _*)
    )(
      s"Inserting ${sp(template)} with existing id",
      saved => s"Milestone template inserted with existing id ${sp(saved)}"
    )
  }

  override def findById(id: UUID): ConnectionIO[MilestoneTemplate] =
    logAroundInfoQuery(findByIdStatementUUID(MILESTONE_TEMPLATE_TABLE_FRAG)(id).query[MilestoneTemplate].unique)(
      s"Finding milestone template with ${sp(id)}",
      found => s"Milestone template found ${sp(found)}"
    )

  override def deleteById(id: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(deleteByIdStatementUUID(MILESTONE_TEMPLATE_TABLE_FRAG)(id).update.run)(
      s"Deleting milestone template with ${sp(id)}",
      _ => s"Milestone template with ${sp(id)} deleted"
    )

  override def findFullById(id: UUID): ConnectionIO[MilestoneTemplateFull] =
    logAroundInfoQuery(
      (fullSelect ++ fr"where t.id = $id")
        .query[(MilestoneTemplate, MilestoneTemplateVersion)]
        .unique
        .flatMap(row => withSteps(List(row)).map(_.head))
    )(
      s"Finding full milestone template with ${sp(id)}",
      found => s"Full milestone template found ${sp(found)}"
    )

  override def findPaginatedFullByProjectId(
    projectId: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[MilestoneTemplateFull]] =
    logAroundInfoQuery(
      TgFindPaginatedQuery[(MilestoneTemplate, MilestoneTemplateVersion)](
        pageQuery = request =>
          (fullSelect ++ fr"where t.project_id = $projectId" ++
            PaginationSql.orderBy(fr"tv.name", fr"t.id") ++
            PaginationSql.limitOffset(request))
            .query[(MilestoneTemplate, MilestoneTemplateVersion)]
            .to[List],
        countQuery = (countStatement(MILESTONE_TEMPLATE_TABLE_FRAG) ++ fr"where project_id = $projectId")
          .query[Long]
          .unique
      ).run(page)
        .flatMap(rows => withSteps(rows.items).map(full => rows.copy(items = full)))
    )(
      s"Finding milestone templates of ${sp(projectId)} with ${sp(page)}",
      found => s"Milestone templates found ${sp(found)} for ${sp(projectId)}"
    )

  override def findNamesUsingStepTemplate(stepTemplateId: UUID): ConnectionIO[List[String]] =
    logAroundInfoQuery(
      (fr"select tv.name from" ++ MILESTONE_TEMPLATE_TABLE_FRAG ++ latestVersionJoin ++
        fr"join" ++ MILESTONE_TEMPLATE_VERSION_STEP_TABLE_FRAG ++ fr"s on s.milestone_template_version = tv.version" ++
        fr"where s.milestone_step_template_id = $stepTemplateId group by tv.name order by tv.name")
        .query[String]
        .to[List]
    )(
      s"Finding milestone templates using step template ${sp(stepTemplateId)}",
      found => s"Milestone templates using step template ${sp(stepTemplateId)} found ${sp(found)}"
    )

  private def withSteps(rows: List[(MilestoneTemplate, MilestoneTemplateVersion)]): ConnectionIO[List[MilestoneTemplateFull]] =
    versionRepo.findStepsByVersions(rows.map(_._2.version)).map { steps =>
      rows.map { case (template, version) =>
        MilestoneTemplateFull(template, MilestoneTemplateVersionWithSteps(version, steps.getOrElse(version.version, Nil)))
      }
    }

  // The latest version of the template (via LATERAL), joined as `tv` to a query that selects from the template as `t`.
  private def latestVersionJoin: Fragment =
    fr"t join lateral (select * from" ++ MILESTONE_TEMPLATE_VERSION_TABLE_FRAG ++
      fr"where milestone_template_id = t.id order by version desc limit 1) tv on true"

  private def fullSelect: Fragment =
    fr"select t.id, t.project_id, t.created_by, t.created_at," ++
      fr"tv.version, tv.milestone_template_id, tv.name, tv.description, tv.created_at from" ++
      MILESTONE_TEMPLATE_TABLE_FRAG ++ latestVersionJoin
}

object MilestoneTemplateRepoImpl {
  val MILESTONE_TEMPLATE_TABLE: String = "milestone_template"
  val MILESTONE_TEMPLATE_TABLE_FRAG: Fragment = Fragment.const(MILESTONE_TEMPLATE_TABLE)
}
