package com.togger.milestonetemplate

import cats.data.NonEmptyList
import cats.implicits._
import com.togger.common.{DbPageRequest, PaginatedResponse}
import com.togger.db.Util._
import com.togger.db.{PaginationSql, TgFindPaginatedQuery}
import com.togger.logging.TgLogging
import com.togger.milestonesteptemplate.MilestoneStepTemplateVersionRepoImpl.MILESTONE_STEP_TEMPLATE_VERSION_TABLE_FRAG
import com.togger.milestonetemplate.MilestoneTemplateVersionRepoImpl.{
  MILESTONE_TEMPLATE_VERSION_STEP_TABLE_FRAG,
  MILESTONE_TEMPLATE_VERSION_TABLE_FRAG
}
import com.togger.util.SplunkMacro.sp
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.fragment.Fragment
import doobie.{ConnectionIO, Fragments, Update}

import java.util.UUID

class MilestoneTemplateVersionRepoImpl extends MilestoneTemplateVersionRepo with TgLogging {

  private lazy val returnFields = List("version", "milestone_template_id", "name", "description", "created_at")

  override def insert(templateVersion: MilestoneTemplateVersion): ConnectionIO[MilestoneTemplateVersion] = {
    import templateVersion._
    logAroundInfoQuery(
      insertStatement(MILESTONE_TEMPLATE_VERSION_TABLE_FRAG)(fr"""
        (milestone_template_id, name, description, created_at)
        values ($milestoneTemplateId, $name, $description, $createdAt)
      """).update
        .withUniqueGeneratedKeys[MilestoneTemplateVersion](returnFields: _*)
    )(s"Inserting ${sp(templateVersion)}", saved => s"Milestone template version inserted ${sp(saved)}")
  }

  override def insertWithId(templateVersion: MilestoneTemplateVersion): ConnectionIO[MilestoneTemplateVersion] = {
    import templateVersion._
    logAroundInfoQuery(
      insertStatement(MILESTONE_TEMPLATE_VERSION_TABLE_FRAG)(fr"""
        (version, milestone_template_id, name, description, created_at)
        values ($version, $milestoneTemplateId, $name, $description, $createdAt)
      """).update
        .withUniqueGeneratedKeys[MilestoneTemplateVersion](returnFields: _*)
    )(
      s"Inserting ${sp(templateVersion)} with existing version",
      saved => s"Milestone template version inserted with existing version ${sp(saved)}"
    )
  }

  override def insertSteps(version: UUID, stepTemplateIds: List[UUID]): ConnectionIO[Int] =
    logAroundInfoQuery(
      Update[(UUID, Int, UUID)](
        (fr"insert into" ++ MILESTONE_TEMPLATE_VERSION_STEP_TABLE_FRAG ++
          fr"(milestone_template_version, position, milestone_step_template_id) values (?, ?, ?)").update.sql
      ).updateMany(stepTemplateIds.zipWithIndex.map { case (stepTemplateId, position) =>
        (version, position, stepTemplateId)
      })
    )(
      s"Inserting steps ${sp(stepTemplateIds)} of milestone template version ${sp(version)}",
      count => s"Milestone template version steps inserted ${sp(count)}"
    )

  override def findByVersion(version: UUID): ConnectionIO[MilestoneTemplateVersion] =
    logAroundInfoQuery(
      (selectAllStatement(MILESTONE_TEMPLATE_VERSION_TABLE_FRAG) ++ fr"where version = $version")
        .query[MilestoneTemplateVersion]
        .unique
    )(
      s"Finding milestone template version with ${sp(version)}",
      found => s"Milestone template version found ${sp(found)}"
    )

  override def findLatestByTemplateId(templateId: UUID): ConnectionIO[MilestoneTemplateVersion] =
    logAroundInfoQuery(
      (selectAllStatement(MILESTONE_TEMPLATE_VERSION_TABLE_FRAG) ++
        fr"where milestone_template_id = $templateId" ++ orderByDescLimitOne(fr"version"))
        .query[MilestoneTemplateVersion]
        .unique
    )(
      s"Finding latest milestone template version for ${sp(templateId)}",
      found => s"Latest milestone template version found ${sp(found)}"
    )

  override def findPaginatedByTemplateId(
    templateId: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[MilestoneTemplateVersion]] =
    logAroundInfoQuery(
      TgFindPaginatedQuery[MilestoneTemplateVersion](
        pageQuery = request =>
          (selectAllStatement(MILESTONE_TEMPLATE_VERSION_TABLE_FRAG) ++ fr"where milestone_template_id = $templateId" ++
            PaginationSql.orderBy(fr"version desc") ++
            PaginationSql.limitOffset(request)).query[MilestoneTemplateVersion].to[List],
        countQuery = (countStatement(MILESTONE_TEMPLATE_VERSION_TABLE_FRAG) ++
          fr"where milestone_template_id = $templateId").query[Long].unique
      ).run(page)
    )(
      s"Finding milestone template versions of ${sp(templateId)} with ${sp(page)}",
      found => s"Milestone template versions found ${sp(found)} for ${sp(templateId)}"
    )

  override def findStepsByVersions(versions: List[UUID]): ConnectionIO[Map[UUID, List[MilestoneTemplateStep]]] =
    NonEmptyList.fromList(versions).fold(Map.empty[UUID, List[MilestoneTemplateStep]].pure[ConnectionIO]) { ids =>
      logAroundInfoQuery(
        (fr"select s.milestone_template_version, s.milestone_step_template_id, stv.name, stv.description from" ++
          MILESTONE_TEMPLATE_VERSION_STEP_TABLE_FRAG ++ fr"s join lateral (select * from" ++
          MILESTONE_STEP_TEMPLATE_VERSION_TABLE_FRAG ++
          fr"where milestone_step_template_id = s.milestone_step_template_id order by version desc limit 1) stv on true" ++
          fr"where" ++ Fragments.in(fr"s.milestone_template_version", ids) ++
          fr"order by s.milestone_template_version, s.position")
          .query[(UUID, UUID, String, String)]
          .to[List]
          .map(
            _.groupMap { case (version, _, _, _) => version } { case (_, stepTemplateId, name, description) =>
              MilestoneTemplateStep(stepTemplateId, name, description)
            }
          )
      )(
        s"Finding steps of milestone template versions ${sp(versions)}",
        found => s"Steps of milestone template versions found ${sp(found)}"
      )
    }
}

object MilestoneTemplateVersionRepoImpl {
  val MILESTONE_TEMPLATE_VERSION_TABLE: String = "milestone_template_version"
  val MILESTONE_TEMPLATE_VERSION_TABLE_FRAG: Fragment = Fragment.const(MILESTONE_TEMPLATE_VERSION_TABLE)

  val MILESTONE_TEMPLATE_VERSION_STEP_TABLE: String = "milestone_template_version_step"
  val MILESTONE_TEMPLATE_VERSION_STEP_TABLE_FRAG: Fragment = Fragment.const(MILESTONE_TEMPLATE_VERSION_STEP_TABLE)
}
