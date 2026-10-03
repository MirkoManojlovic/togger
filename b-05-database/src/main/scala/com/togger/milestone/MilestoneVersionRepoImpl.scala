package com.togger.milestone

import com.togger.db.Util._
import com.togger.milestone.MilestoneVersionRepoImpl.MILESTONE_VERSION_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.{ConnectionIO, Fragment, Meta}

import java.util.UUID

class MilestoneVersionRepoImpl extends MilestoneVersionRepo {
  import MilestoneVersionRepoImpl.milestoneStatusCodec

  private lazy val returnFields = List(
    "version",
    "milestone_id",
    "name",
    "description",
    "status",
    "start_date",
    "due_date",
    "end_date",
    "created_at",
    "updated_at"
  )

  def insert(milestoneVersion: MilestoneVersion): ConnectionIO[MilestoneVersion] = {
    import milestoneVersion._
    logAroundInfoQuery(
      insertStatement(MILESTONE_VERSION_TABLE_FRAG)(fr"""
        (milestone_id, name, description, status, start_date, due_date, end_date, created_at, updated_at)
        values ($milestoneId, $name, $description, $status, $startDate, $dueDate, $endDate, $createdAt, $updatedAt)
      """).update
        .withUniqueGeneratedKeys[MilestoneVersion](returnFields: _*)
    )(s"Inserting ${sp(milestoneVersion)}", milestoneVersion => s"Milestone version inserted ${sp(milestoneVersion)}")
  }

  override def findByVersion(version: UUID): ConnectionIO[MilestoneVersion] =
    logAroundInfoQuery(
      (selectAllStatement(MILESTONE_VERSION_TABLE_FRAG) ++ fr"where version = $version").query[MilestoneVersion].unique
    )(
      s"Finding milestone version with ${sp(version)}",
      milestoneVersion => s"Milestone version found ${sp(milestoneVersion)}"
    )

  override def findByMilestoneId(milestoneId: UUID): ConnectionIO[List[MilestoneVersion]] =
    logAroundInfoQuery(
      (selectAllStatement(MILESTONE_VERSION_TABLE_FRAG) ++
        fr"where milestone_id = $milestoneId order by version desc").query[MilestoneVersion].to[List]
    )(
      s"Finding milestone versions for ${sp(milestoneId)}",
      milestoneVersions => s"Milestone versions found ${sp(milestoneVersions)} for ${sp(milestoneId)}"
    )

  override def findLatestByMilestoneId(milestoneId: UUID): ConnectionIO[MilestoneVersion] =
    logAroundInfoQuery(
      (selectAllStatement(MILESTONE_VERSION_TABLE_FRAG) ++
        fr"where milestone_id = $milestoneId" ++ orderByDescLimitOne(fr"version")).query[MilestoneVersion].unique
    )(
      s"Finding latest milestone version for ${sp(milestoneId)}",
      milestoneVersion => s"Latest milestone version found ${sp(milestoneVersion)}"
    )

  override def updateContent(milestoneVersion: MilestoneVersion): ConnectionIO[MilestoneVersion] =
    logAroundInfoQuery(
      (fr"update" ++ MILESTONE_VERSION_TABLE_FRAG ++ fr"""set
            name = ${milestoneVersion.name},
            description = ${milestoneVersion.description},
            status = ${milestoneVersion.status},
            start_date = ${milestoneVersion.startDate},
            due_date = ${milestoneVersion.dueDate},
            end_date = ${milestoneVersion.endDate},
            updated_at = ${milestoneVersion.updatedAt}
          where version = ${milestoneVersion.version}""").update
        .withUniqueGeneratedKeys[MilestoneVersion](returnFields: _*)
    )(s"Updating ${sp(milestoneVersion)}", milestoneVersion => s"${sp(milestoneVersion)} updated")

  override def deleteByVersion(version: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(
      (deleteStatement(MILESTONE_VERSION_TABLE_FRAG) ++ fr"where version = $version").update.run
    )(
      s"Deleting milestone version with ${sp(version)}",
      numDeleted => s"Milestone version deleted ${sp(numDeleted)}"
    )

  override def countByMilestoneId(milestoneId: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(
      (countStatement(MILESTONE_VERSION_TABLE_FRAG) ++ fr"where milestone_id = $milestoneId").query[Int].unique
    )(
      s"Counting milestone versions for ${sp(milestoneId)}",
      count => s"Milestone version count ${sp(count)} for ${sp(milestoneId)}"
    )
}

object MilestoneVersionRepoImpl {
  val MILESTONE_VERSION_TABLE: String = "milestone_version"
  val MILESTONE_VERSION_TABLE_FRAG: Fragment = Fragment.const(MILESTONE_VERSION_TABLE)

  implicit val milestoneStatusCodec: Meta[MilestoneStatus] = Meta[Int].imap(MilestoneStatus.withValue)(_.value)
}
