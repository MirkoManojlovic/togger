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
        (task_id, created_at)
        values ($taskId, $createdAt)
      """).update
        .withUniqueGeneratedKeys[Link](
          "id",
          "task_id",
          "created_at"
        )
    )(s"Inserting ${sp(link)}", link => s"Link inserted ${sp(link)}")
  }

  override def insertWithId(link: Link): ConnectionIO[Link] = {
    import link._
    logAroundInfoQuery(
      insertStatement(LINK_TABLE_FRAG)(fr"""
        (id, task_id, created_at)
        values ($id, $taskId, $createdAt)
      """).update
        .withUniqueGeneratedKeys[Link](
          "id",
          "task_id",
          "created_at"
        )
    )(s"Inserting ${sp(link)} with existing id", link => s"Link inserted with existing id ${sp(link)}")
  }

  type LinkInfo = (UUID, Instant)
  private val insertManySql = "insert into link (task_id, created_at) values (?, ?)"

  override def insertMany(links: Seq[Link]): ConnectionIO[List[Link]] = {
    logAroundInfoQuery {
      val linksToSave = links.map(link => (link.taskId, link.createdAt))
      Update[LinkInfo](insertManySql)
        .updateManyWithGeneratedKeys[Link]("id", "task_id", "created_at")(linksToSave)
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

  override def deleteById(id: UUID): ConnectionIO[Int] = {
    logAroundInfoQuery(deleteByIdStatementUUID(LINK_TABLE_FRAG)(id).update.run)(
      s"Deleting link with ${sp(id)}",
      link => s"Link deleted ${sp(link)}"
    )
  }

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
