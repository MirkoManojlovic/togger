package com.togger.milestone

import com.togger.db.Util._
import com.togger.milestone.MilestoneDateVersionRepoImpl.MILESTONE_DATE_VERSION_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.{ConnectionIO, Fragment}

import java.util.UUID

class MilestoneDateVersionRepoImpl extends MilestoneDateVersionRepo {

  private lazy val returnFields = List(
    "version",
    "milestone_date_id",
    "name",
    "description",
    "date",
    "created_at",
    "updated_at"
  )

  def insert(milestoneDateVersion: MilestoneDateVersion): ConnectionIO[MilestoneDateVersion] = {
    import milestoneDateVersion._
    logAroundInfoQuery(
      insertStatement(MILESTONE_DATE_VERSION_TABLE_FRAG)(fr"""
        (milestone_date_id, name, description, date, created_at, updated_at)
        values ($milestoneDateId, $name, $description, $date, $createdAt, $updatedAt)
      """).update
        .withUniqueGeneratedKeys[MilestoneDateVersion](returnFields: _*)
    )(
      s"Inserting ${sp(milestoneDateVersion)}",
      milestoneDateVersion => s"Milestone date version inserted ${sp(milestoneDateVersion)}"
    )
  }

  override def findByVersion(version: UUID): ConnectionIO[MilestoneDateVersion] =
    logAroundInfoQuery(
      (selectAllStatement(MILESTONE_DATE_VERSION_TABLE_FRAG) ++ fr"where version = $version")
        .query[MilestoneDateVersion]
        .unique
    )(
      s"Finding milestone date version with ${sp(version)}",
      milestoneDateVersion => s"Milestone date version found ${sp(milestoneDateVersion)}"
    )

  override def findByMilestoneDateId(milestoneDateId: UUID): ConnectionIO[List[MilestoneDateVersion]] =
    logAroundInfoQuery(
      (selectAllStatement(MILESTONE_DATE_VERSION_TABLE_FRAG) ++
        fr"where milestone_date_id = $milestoneDateId order by version desc").query[MilestoneDateVersion].to[List]
    )(
      s"Finding milestone date versions for ${sp(milestoneDateId)}",
      milestoneDateVersions => s"Milestone date versions found ${sp(milestoneDateVersions)} for ${sp(milestoneDateId)}"
    )

  override def findLatestByMilestoneDateId(milestoneDateId: UUID): ConnectionIO[MilestoneDateVersion] =
    logAroundInfoQuery(
      (selectAllStatement(MILESTONE_DATE_VERSION_TABLE_FRAG) ++
        fr"where milestone_date_id = $milestoneDateId" ++ orderByDescLimitOne(fr"version"))
        .query[MilestoneDateVersion]
        .unique
    )(
      s"Finding latest milestone date version for ${sp(milestoneDateId)}",
      milestoneDateVersion => s"Latest milestone date version found ${sp(milestoneDateVersion)}"
    )

  override def updateContent(milestoneDateVersion: MilestoneDateVersion): ConnectionIO[MilestoneDateVersion] =
    logAroundInfoQuery(
      (fr"update" ++ MILESTONE_DATE_VERSION_TABLE_FRAG ++ fr"""set
            name = ${milestoneDateVersion.name},
            description = ${milestoneDateVersion.description},
            date = ${milestoneDateVersion.date},
            updated_at = ${milestoneDateVersion.updatedAt}
          where version = ${milestoneDateVersion.version}""").update
        .withUniqueGeneratedKeys[MilestoneDateVersion](returnFields: _*)
    )(s"Updating ${sp(milestoneDateVersion)}", milestoneDateVersion => s"${sp(milestoneDateVersion)} updated")

  override def deleteByVersion(version: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(
      (deleteStatement(MILESTONE_DATE_VERSION_TABLE_FRAG) ++ fr"where version = $version").update.run
    )(
      s"Deleting milestone date version with ${sp(version)}",
      numDeleted => s"Milestone date version deleted ${sp(numDeleted)}"
    )

  override def countByMilestoneDateId(milestoneDateId: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(
      (countStatement(MILESTONE_DATE_VERSION_TABLE_FRAG) ++ fr"where milestone_date_id = $milestoneDateId")
        .query[Int]
        .unique
    )(
      s"Counting milestone date versions for ${sp(milestoneDateId)}",
      count => s"Milestone date version count ${sp(count)} for ${sp(milestoneDateId)}"
    )
}

object MilestoneDateVersionRepoImpl {
  val MILESTONE_DATE_VERSION_TABLE: String = "milestone_date_version"
  val MILESTONE_DATE_VERSION_TABLE_FRAG: Fragment = Fragment.const(MILESTONE_DATE_VERSION_TABLE)
}
