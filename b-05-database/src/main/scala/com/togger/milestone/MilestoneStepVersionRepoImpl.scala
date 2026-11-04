package com.togger.milestone

import cats.data.NonEmptyList
import cats.implicits._
import com.togger.common.DbPageRequest
import com.togger.common.PaginatedResponse
import com.togger.db.PaginationSql
import com.togger.db.TgFindPaginatedQuery
import com.togger.db.Util._
import com.togger.milestone.MilestoneRepoImpl.MILESTONE_TABLE_FRAG
import com.togger.milestone.MilestoneStepRepoImpl.MILESTONE_STEP_TABLE_FRAG
import com.togger.milestone.MilestoneStepVersionRepoImpl.MILESTONE_STEP_VERSION_TABLE_FRAG
import com.togger.milestone.MilestoneStepVersionRepoImpl.milestoneStepStatusCodec
import com.togger.util.SplunkMacro.sp
import doobie.ConnectionIO
import doobie.Fragment
import doobie.Fragments
import doobie.Meta
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

class MilestoneStepVersionRepoImpl extends MilestoneStepVersionRepo {

  private lazy val returnFields = List(
    "version",
    "milestone_step_id",
    "name",
    "description",
    "status",
    "position",
    "start_date",
    "due_date",
    "end_date",
    "created_at",
    "updated_at",
    "change_comment"
  )

  def insert(milestoneStepVersion: MilestoneStepVersion): ConnectionIO[MilestoneStepVersion] = {
    import milestoneStepVersion._
    logAroundInfoQuery(
      insertStatement(MILESTONE_STEP_VERSION_TABLE_FRAG)(fr"""
        (milestone_step_id, name, description, status, position, start_date, due_date, end_date, created_at, updated_at, change_comment)
        values ($milestoneStepId, $name, $description, $status, $position, $startDate, $dueDate, $endDate, $createdAt, $updatedAt, $changeComment)
      """).update
        .withUniqueGeneratedKeys[MilestoneStepVersion](returnFields: _*)
    )(
      s"Inserting ${sp(milestoneStepVersion)}",
      milestoneStepVersion => s"Milestone step version inserted ${sp(milestoneStepVersion)}"
    )
  }

  override def insertWithId(milestoneStepVersion: MilestoneStepVersion): ConnectionIO[MilestoneStepVersion] = {
    import milestoneStepVersion._
    logAroundInfoQuery(
      insertStatement(MILESTONE_STEP_VERSION_TABLE_FRAG)(fr"""
        (version, milestone_step_id, name, description, status, position, start_date, due_date, end_date, created_at, updated_at, change_comment)
        values ($version, $milestoneStepId, $name, $description, $status, $position, $startDate, $dueDate, $endDate, $createdAt, $updatedAt, $changeComment)
      """).update
        .withUniqueGeneratedKeys[MilestoneStepVersion](returnFields: _*)
    )(
      s"Inserting ${sp(milestoneStepVersion)} with existing version",
      milestoneStepVersion => s"Milestone step version inserted with existing version ${sp(milestoneStepVersion)}"
    )
  }

  override def findByVersion(version: UUID): ConnectionIO[MilestoneStepVersion] =
    logAroundInfoQuery(
      (selectAllStatement(MILESTONE_STEP_VERSION_TABLE_FRAG) ++ fr"where version = $version")
        .query[MilestoneStepVersion]
        .unique
    )(
      s"Finding milestone step version with ${sp(version)}",
      milestoneStepVersion => s"Milestone step version found ${sp(milestoneStepVersion)}"
    )

  override def findPaginatedByMilestoneStepId(
    milestoneStepId: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[MilestoneStepVersion]] =
    logAroundInfoQuery(
      TgFindPaginatedQuery[MilestoneStepVersion](
        pageQuery = request =>
          (selectAllStatement(MILESTONE_STEP_VERSION_TABLE_FRAG) ++ fr"where milestone_step_id = $milestoneStepId" ++
            PaginationSql.orderBy(fr"version desc") ++
            PaginationSql.limitOffset(request)).query[MilestoneStepVersion].to[List],
        countQuery = (fr"select count(*) from" ++ MILESTONE_STEP_VERSION_TABLE_FRAG ++
          fr"where milestone_step_id = $milestoneStepId").query[Long].unique
      ).run(page)
    )(
      s"Finding milestone step versions of ${sp(milestoneStepId)} with ${sp(page)}",
      found => s"Milestone step versions found ${sp(found)} for ${sp(milestoneStepId)}"
    )

  override def findLatestByMilestoneStepId(milestoneStepId: UUID): ConnectionIO[MilestoneStepVersion] =
    logAroundInfoQuery(
      (selectAllStatement(MILESTONE_STEP_VERSION_TABLE_FRAG) ++
        fr"where milestone_step_id = $milestoneStepId" ++ orderByDescLimitOne(fr"version"))
        .query[MilestoneStepVersion]
        .unique
    )(
      s"Finding latest milestone step version for ${sp(milestoneStepId)}",
      milestoneStepVersion => s"Latest milestone step version found ${sp(milestoneStepVersion)}"
    )

  override def findLatestByMilestoneId(milestoneId: UUID): ConnectionIO[List[MilestoneStepVersion]] =
    logAroundInfoQuery(
      (fr"select distinct on (v.milestone_step_id)" ++ Fragment.const(returnFields.map("v." + _).mkString(", ")) ++
        fr"from" ++ MILESTONE_STEP_VERSION_TABLE_FRAG ++ fr"v" ++
        innerJoin(MILESTONE_STEP_TABLE_FRAG)(fr"v.milestone_step_id = milestone_step.id") ++
        fr"where milestone_step.milestone_id = $milestoneId order by v.milestone_step_id, v.version desc")
        .query[MilestoneStepVersion]
        .to[List]
    )(
      s"Finding latest milestone step versions of ${sp(milestoneId)}",
      found => s"Latest milestone step versions found ${sp(found)} for ${sp(milestoneId)}"
    )

  override def findLatestByProjectId(projectId: UUID): ConnectionIO[List[MilestoneStepVersion]] =
    logAroundInfoQuery(
      (fr"select distinct on (v.milestone_step_id)" ++ Fragment.const(returnFields.map("v." + _).mkString(", ")) ++
        fr"from" ++ MILESTONE_STEP_VERSION_TABLE_FRAG ++ fr"v" ++
        innerJoin(MILESTONE_STEP_TABLE_FRAG)(fr"v.milestone_step_id = milestone_step.id") ++
        innerJoin(MILESTONE_TABLE_FRAG)(fr"milestone.id = milestone_step.milestone_id") ++
        fr"where milestone.project_id = $projectId order by v.milestone_step_id, v.version desc")
        .query[MilestoneStepVersion]
        .to[List]
    )(
      s"Finding latest milestone step versions of project ${sp(projectId)}",
      found => s"Latest milestone step versions found ${sp(found.size)} for project ${sp(projectId)}"
    )

  override def countByMilestoneIds(milestoneIds: List[UUID]): ConnectionIO[Map[UUID, MilestoneStepCounts]] =
    NonEmptyList.fromList(milestoneIds).fold(Map.empty[UUID, MilestoneStepCounts].pure[ConnectionIO]) { ids =>
      logAroundInfoQuery(
        (fr"""select milestone_step.milestone_id, latest.status
              from""" ++ MILESTONE_STEP_TABLE_FRAG ++ fr"""
              join lateral (
                select status from""" ++ MILESTONE_STEP_VERSION_TABLE_FRAG ++ fr"""
                where milestone_step_id = milestone_step.id
                order by version desc limit 1
              ) latest on true
              where""" ++ Fragments.in(fr"milestone_step.milestone_id", ids))
          .query[(UUID, MilestoneStepStatus)]
          .to[List]
          .map(
            _.groupMap { case (milestoneId, _) => milestoneId } { case (_, status) => status }.view
              .mapValues(MilestoneStepCounts.fromStatuses)
              .toMap
          )
      )(
        s"Counting milestone steps of ${sp(milestoneIds)}",
        found => s"Milestone step counts found ${sp(found)}"
      )
    }

}

object MilestoneStepVersionRepoImpl {
  val MILESTONE_STEP_VERSION_TABLE: String = "milestone_step_version"
  val MILESTONE_STEP_VERSION_TABLE_FRAG: Fragment = Fragment.const(MILESTONE_STEP_VERSION_TABLE)

  implicit val milestoneStepStatusCodec: Meta[MilestoneStepStatus] =
    Meta[Int].imap(MilestoneStepStatus.withValue)(_.value)
}
