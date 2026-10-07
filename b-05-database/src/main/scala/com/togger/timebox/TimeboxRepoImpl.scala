package com.togger.timebox

import com.togger.db.Util._
import com.togger.timebox.TimeboxRepoImpl.TIMEBOX_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.{ConnectionIO, Fragment}

import java.util.UUID

class TimeboxRepoImpl extends TimeboxRepo {

  override def insert(timebox: Timebox): ConnectionIO[Timebox] = {
    import timebox._
    logAroundInfoQuery(
      insertStatement(TIMEBOX_TABLE_FRAG)(fr"""
        (project_id, created_at)
        values ($projectId, $createdAt)
      """).update
        .withUniqueGeneratedKeys[Timebox]("id", "project_id", "created_at")
    )(s"Inserting ${sp(timebox)}", timebox => s"Timebox inserted ${sp(timebox)}")
  }

  override def findById(id: UUID): ConnectionIO[Timebox] =
    logAroundInfoQuery(findByIdStatementUUID(TIMEBOX_TABLE_FRAG)(id).query[Timebox].unique)(
      s"Finding timebox with ${sp(id)}",
      timebox => s"Timebox found ${sp(timebox)}"
    )

  override def deleteById(id: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(deleteByIdStatementUUID(TIMEBOX_TABLE_FRAG)(id).update.run)(
      s"Deleting timebox with ${sp(id)}",
      numDeleted => s"Timebox deleted ${sp(numDeleted)}"
    )

  override def findByProjectId(projectId: UUID): ConnectionIO[List[Timebox]] =
    logAroundInfoQuery(
      (selectAllStatement(TIMEBOX_TABLE_FRAG) ++ fr"where project_id = $projectId order by id desc")
        .query[Timebox]
        .to[List]
    )(
      s"Finding timeboxes for ${sp(projectId)}",
      timeboxes => s"Timeboxes found ${sp(timeboxes)} for ${sp(projectId)}"
    )

}

object TimeboxRepoImpl {
  val TIMEBOX_TABLE: String = "timebox"
  val TIMEBOX_TABLE_FRAG: Fragment = Fragment.const(TIMEBOX_TABLE)
}
