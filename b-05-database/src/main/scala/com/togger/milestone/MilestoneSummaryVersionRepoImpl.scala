package com.togger.milestone

import com.togger.common.{DbPageRequest, PaginatedResponse}
import com.togger.db.Util._
import com.togger.db.{PaginationSql, TgFindPaginatedQuery}
import com.togger.milestone.MilestoneSummaryVersionRepoImpl.MILESTONE_SUMMARY_VERSION_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.{ConnectionIO, Fragment}

import java.util.UUID

class MilestoneSummaryVersionRepoImpl extends MilestoneSummaryVersionRepo {

  private lazy val returnFields = List(
    "version",
    "milestone_summary_id",
    "summary",
    "start_date",
    "end_date",
    "created_at",
    "updated_at"
  )

  def insert(milestoneSummaryVersion: MilestoneSummaryVersion): ConnectionIO[MilestoneSummaryVersion] = {
    import milestoneSummaryVersion._
    logAroundInfoQuery(
      insertStatement(MILESTONE_SUMMARY_VERSION_TABLE_FRAG)(fr"""
        (milestone_summary_id, summary, start_date, end_date, created_at, updated_at)
        values ($milestoneSummaryId, $summary, $startDate, $endDate, $createdAt, $updatedAt)
      """).update
        .withUniqueGeneratedKeys[MilestoneSummaryVersion](returnFields: _*)
    )(
      s"Inserting ${sp(milestoneSummaryVersion)}",
      milestoneSummaryVersion => s"Milestone summary version inserted ${sp(milestoneSummaryVersion)}"
    )
  }

  override def insertWithId(milestoneSummaryVersion: MilestoneSummaryVersion): ConnectionIO[MilestoneSummaryVersion] = {
    import milestoneSummaryVersion._
    logAroundInfoQuery(
      insertStatement(MILESTONE_SUMMARY_VERSION_TABLE_FRAG)(fr"""
        (version, milestone_summary_id, summary, start_date, end_date, created_at, updated_at)
        values ($version, $milestoneSummaryId, $summary, $startDate, $endDate, $createdAt, $updatedAt)
      """).update
        .withUniqueGeneratedKeys[MilestoneSummaryVersion](returnFields: _*)
    )(
      s"Inserting ${sp(milestoneSummaryVersion)} with existing version",
      milestoneSummaryVersion => s"Milestone summary version inserted with existing version ${sp(milestoneSummaryVersion)}"
    )
  }

  override def findByVersion(version: UUID): ConnectionIO[MilestoneSummaryVersion] =
    logAroundInfoQuery(
      (selectAllStatement(MILESTONE_SUMMARY_VERSION_TABLE_FRAG) ++ fr"where version = $version")
        .query[MilestoneSummaryVersion]
        .unique
    )(
      s"Finding milestone summary version with ${sp(version)}",
      milestoneSummaryVersion => s"Milestone summary version found ${sp(milestoneSummaryVersion)}"
    )

  override def findPaginatedByMilestoneSummaryId(
    milestoneSummaryId: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[MilestoneSummaryVersion]] =
    logAroundInfoQuery(
      TgFindPaginatedQuery[MilestoneSummaryVersion](
        pageQuery = request =>
          (selectAllStatement(MILESTONE_SUMMARY_VERSION_TABLE_FRAG) ++ fr"where milestone_summary_id = $milestoneSummaryId" ++
            PaginationSql.orderBy(fr"version desc") ++
            PaginationSql.limitOffset(request)).query[MilestoneSummaryVersion].to[List],
        countQuery = (fr"select count(*) from" ++ MILESTONE_SUMMARY_VERSION_TABLE_FRAG ++
          fr"where milestone_summary_id = $milestoneSummaryId").query[Long].unique
      ).run(page)
    )(
      s"Finding milestone summary versions of ${sp(milestoneSummaryId)} with ${sp(page)}",
      found => s"Milestone summary versions found ${sp(found)} for ${sp(milestoneSummaryId)}"
    )

  override def findLatestByMilestoneSummaryId(milestoneSummaryId: UUID): ConnectionIO[MilestoneSummaryVersion] =
    logAroundInfoQuery(
      (selectAllStatement(MILESTONE_SUMMARY_VERSION_TABLE_FRAG) ++
        fr"where milestone_summary_id = $milestoneSummaryId" ++ orderByDescLimitOne(fr"version"))
        .query[MilestoneSummaryVersion]
        .unique
    )(
      s"Finding latest milestone summary version for ${sp(milestoneSummaryId)}",
      milestoneSummaryVersion => s"Latest milestone summary version found ${sp(milestoneSummaryVersion)}"
    )

  override def updateContent(milestoneSummaryVersion: MilestoneSummaryVersion): ConnectionIO[MilestoneSummaryVersion] =
    logAroundInfoQuery(
      (fr"update" ++ MILESTONE_SUMMARY_VERSION_TABLE_FRAG ++ fr"""set
            summary = ${milestoneSummaryVersion.summary},
            start_date = ${milestoneSummaryVersion.startDate},
            end_date = ${milestoneSummaryVersion.endDate},
            updated_at = ${milestoneSummaryVersion.updatedAt}
          where version = ${milestoneSummaryVersion.version}""").update
        .withUniqueGeneratedKeys[MilestoneSummaryVersion](returnFields: _*)
    )(s"Updating ${sp(milestoneSummaryVersion)}", milestoneSummaryVersion => s"${sp(milestoneSummaryVersion)} updated")

  override def deleteByVersion(version: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(
      (deleteStatement(MILESTONE_SUMMARY_VERSION_TABLE_FRAG) ++ fr"where version = $version").update.run
    )(
      s"Deleting milestone summary version with ${sp(version)}",
      numDeleted => s"Milestone summary version deleted ${sp(numDeleted)}"
    )

  override def countByMilestoneSummaryId(milestoneSummaryId: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(
      (countStatement(MILESTONE_SUMMARY_VERSION_TABLE_FRAG) ++ fr"where milestone_summary_id = $milestoneSummaryId")
        .query[Int]
        .unique
    )(
      s"Counting milestone summary versions for ${sp(milestoneSummaryId)}",
      count => s"Milestone summary version count ${sp(count)} for ${sp(milestoneSummaryId)}"
    )
}

object MilestoneSummaryVersionRepoImpl {
  val MILESTONE_SUMMARY_VERSION_TABLE: String = "milestone_summary_version"
  val MILESTONE_SUMMARY_VERSION_TABLE_FRAG: Fragment = Fragment.const(MILESTONE_SUMMARY_VERSION_TABLE)
}
