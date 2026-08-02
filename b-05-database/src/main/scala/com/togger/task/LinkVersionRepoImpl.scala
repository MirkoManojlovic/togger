package com.togger.task

import com.togger.db.Util._
import com.togger.task.LinkVersionRepoImpl.LINK_VERSION_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.{ConnectionIO, Fragment}

import java.util.UUID

class LinkVersionRepoImpl extends LinkVersionRepo {

  private lazy val returnFields = List(
    "version",
    "link_id",
    "name",
    "url",
    "created_at",
    "updated_at"
  )

  def insert(linkVersion: LinkVersion): ConnectionIO[LinkVersion] = {
    import linkVersion._
    logAroundInfoQuery(
      insertStatement(LINK_VERSION_TABLE_FRAG)(fr"""
        (link_id, name, url, created_at, updated_at)
        values ($linkId, $name, $url, $createdAt, $updatedAt)
      """).update
        .withUniqueGeneratedKeys[LinkVersion](returnFields: _*)
    )(s"Inserting ${sp(linkVersion)}", linkVersion => s"Link version inserted ${sp(linkVersion)}")
  }

  override def findByVersion(version: UUID): ConnectionIO[LinkVersion] =
    logAroundInfoQuery(
      (selectAllStatement(LINK_VERSION_TABLE_FRAG) ++ fr"where version = $version").query[LinkVersion].unique
    )(
      s"Finding link version with ${sp(version)}",
      linkVersion => s"Link version found ${sp(linkVersion)}"
    )

  override def findByLinkId(linkId: UUID): ConnectionIO[List[LinkVersion]] =
    logAroundInfoQuery(
      (selectAllStatement(LINK_VERSION_TABLE_FRAG) ++
        fr"where link_id = $linkId order by version desc").query[LinkVersion].to[List]
    )(
      s"Finding link versions for ${sp(linkId)}",
      linkVersions => s"Link versions found ${sp(linkVersions)} for ${sp(linkId)}"
    )

  override def findLatestByLinkId(linkId: UUID): ConnectionIO[LinkVersion] =
    logAroundInfoQuery(
      (selectAllStatement(LINK_VERSION_TABLE_FRAG) ++
        fr"where link_id = $linkId" ++ orderByDescLimitOne(fr"version")).query[LinkVersion].unique
    )(
      s"Finding latest link version for ${sp(linkId)}",
      linkVersion => s"Latest link version found ${sp(linkVersion)}"
    )

  override def updateContent(linkVersion: LinkVersion): ConnectionIO[LinkVersion] =
    logAroundInfoQuery(
      (fr"update" ++ LINK_VERSION_TABLE_FRAG ++ fr"""set
            name = ${linkVersion.name},
            url = ${linkVersion.url},
            updated_at = ${linkVersion.updatedAt}
          where version = ${linkVersion.version}""").update
        .withUniqueGeneratedKeys[LinkVersion](returnFields: _*)
    )(s"Updating ${sp(linkVersion)}", linkVersion => s"${sp(linkVersion)} updated")

  override def deleteByVersion(version: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(
      (deleteStatement(LINK_VERSION_TABLE_FRAG) ++ fr"where version = $version").update.run
    )(
      s"Deleting link version with ${sp(version)}",
      numDeleted => s"Link version deleted ${sp(numDeleted)}"
    )

  override def countByLinkId(linkId: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(
      (countStatement(LINK_VERSION_TABLE_FRAG) ++ fr"where link_id = $linkId").query[Int].unique
    )(
      s"Counting link versions for ${sp(linkId)}",
      count => s"Link version count ${sp(count)} for ${sp(linkId)}"
    )
}

object LinkVersionRepoImpl {
  val LINK_VERSION_TABLE: String = "link_version"
  val LINK_VERSION_TABLE_FRAG: Fragment = Fragment.const(LINK_VERSION_TABLE)
}
