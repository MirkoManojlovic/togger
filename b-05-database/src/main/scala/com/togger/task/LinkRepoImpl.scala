package com.togger.task

import com.togger.db.Util._
import com.togger.logging.TgLogging
import com.togger.task.LinkRepoImpl.LINK_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.update.Update
import doobie.{ConnectionIO, Fragment}

import java.time.Instant
import java.util.UUID

class LinkRepoImpl extends LinkRepo with TgLogging {

  override def insert(link: Link): ConnectionIO[Link] = {
    import link._
    logAroundInfoQuery(
      insertStatement(LINK_TABLE_FRAG)(fr"""
        (name, url, task_id, created_at, updated_at)
        values ($name, $url, $taskId, $createdAt, $updatedAt)
      """).update
        .withUniqueGeneratedKeys[Link](
          "id",
          "name",
          "url",
          "task_id",
          "created_at",
          "updated_at"
        )
    )(s"Inserting ${sp(link)}", link => s"Link inserted ${sp(link)}")
  }

  type LinkInfo = (String, String, UUID, Instant, Instant)
  private val insertManySql = "insert into link (name, url, task_id, created_at, updated_at) values (?, ?, ?, ?, ?)"

  override def insertMany(links: Seq[Link]): ConnectionIO[List[Link]] = {
    logAroundInfoQuery {
      val linksToSave = links.map(link => (link.name, link.url, link.taskId, link.createdAt, link.updatedAt))
      Update[LinkInfo](insertManySql)
        .updateManyWithGeneratedKeys[Link]("id", "name", "url", "task_id", "created_at", "updated_at")(linksToSave)
        .compile
        .toList
    }(
      s"Inserting many links ${sp(links)}",
      insertedLinks => s"Links inserted ${sp(insertedLinks)}"
    )
  }

  override def findById(id: UUID): ConnectionIO[Link] = {
    logAroundInfoQuery(findByIdStatementUUID(LINK_TABLE_FRAG)(id).query[Link].unique)(
      s"Finding link with ${sp(id)}",
      link => s"Link found ${sp(link)}"
    )
  }

  def findByUrl(url: String): ConnectionIO[Link] = {
    logAroundInfoQuery(
      (selectAllStatement(LINK_TABLE_FRAG) ++ fr"where url = $url").query[Link].unique
    )(
      s"Finding link with url=$url",
      link => s"Link found $link"
    )
  }

  def findByName(name: String): ConnectionIO[Link] = {
    logAroundInfoQuery(
      (selectAllStatement(LINK_TABLE_FRAG) ++ fr"where name = $name").query[Link].unique
    )(
      s"Finding link with name=$name",
      link => s"Link found $link"
    )
  }

  override def deleteById(id: UUID): ConnectionIO[Int] = {
    logAroundInfoQuery(deleteByIdStatementUUID(LINK_TABLE_FRAG)(id).update.run)(
      s"Deleting link with ${sp(id)}",
      link => s"Link deleted ${sp(link)}"
    )
  }

  override def update(link: Link, updatedAt: Instant): ConnectionIO[Link] =
    logAroundInfoQuery(
      updateStatementUUID(LINK_TABLE_FRAG)(link.id)(fr"""
            name = ${link.name}, 
            url = ${link.url},
            task_id = ${link.taskId},
            updated_at = $updatedAt
          """).update
        .withUniqueGeneratedKeys[Link](
          "id",
          "name",
          "url",
          "task_id",
          "created_at",
          "updated_at"
        )
    )(s"Updating ${sp(link)}", link => s"${sp(link)} updated")

  override def findByTaskId(taskId: UUID): ConnectionIO[List[Link]] =
    logAroundInfoQuery(
      (selectAllStatement(LINK_TABLE_FRAG) ++ fr"where task_id = $taskId").query[Link].to[List]
    )(
      s"Finding links for ${sp(taskId)}",
      links => s"Links found ${sp(links)} for ${sp(taskId)}"
    )

  override def deleteByTaskId(taskId: UUID): ConnectionIO[Int] = {
    logAroundInfoQuery(
      (fr"delete from " ++ LINK_TABLE_FRAG ++ fr"where task_id = $taskId").update.run
    )(
      s"Deleting all links for ${sp(taskId)}",
      numOfLinksDeleted => s"Links deleted ${sp(numOfLinksDeleted)} for ${sp(taskId)}"
    )
  }
}

object LinkRepoImpl {
  val LINK_TABLE: String = "link"
  val LINK_TABLE_FRAG: Fragment = Fragment.const(LINK_TABLE)
}
