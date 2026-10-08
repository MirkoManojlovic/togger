package com.togger.timebox

import com.togger.common.{DbPageRequest, PaginatedResponse}
import com.togger.db.Util._
import com.togger.db.{PaginationSql, TgFindPaginatedQuery}
import com.togger.task.TaskVersion
import com.togger.task.TaskVersionRepoImpl.{taskPriorityCodec, TASK_VERSION_TABLE_FRAG}
import com.togger.timebox.TimeboxVersionRepoImpl.{TIMEBOX_VERSION_TABLE_FRAG, TIMEBOX_VERSION_TASK_TABLE_FRAG}
import com.togger.util.SplunkMacro.sp
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.{ConnectionIO, Fragment, Update}

import java.util.UUID

class TimeboxVersionRepoImpl extends TimeboxVersionRepo {

  private lazy val returnFields = List("version", "timebox_id", "start_date", "end_date", "description", "created_at")

  override def insert(timeboxVersion: TimeboxVersion): ConnectionIO[TimeboxVersion] = {
    import timeboxVersion._
    logAroundInfoQuery(
      insertStatement(TIMEBOX_VERSION_TABLE_FRAG)(fr"""
        (timebox_id, start_date, end_date, description, created_at)
        values ($timeboxId, $startDate, $endDate, $description, $createdAt)
      """).update
        .withUniqueGeneratedKeys[TimeboxVersion](returnFields: _*)
    )(s"Inserting ${sp(timeboxVersion)}", saved => s"Timebox version inserted ${sp(saved)}")
  }

  override def insertPlannedTasks(timeboxVersion: UUID, taskVersions: Seq[UUID]): ConnectionIO[Int] =
    logAroundInfoQuery(
      Update[(UUID, UUID)](
        s"insert into ${TimeboxVersionRepoImpl.TIMEBOX_VERSION_TASK_TABLE} (timebox_version, task_version) values (?, ?)"
      ).updateMany(taskVersions.toList.map(timeboxVersion -> _))
    )(
      s"Inserting planned tasks ${sp(taskVersions)} for ${sp(timeboxVersion)}",
      count => s"Planned tasks inserted ${sp(count)}"
    )

  override def findByVersion(version: UUID): ConnectionIO[TimeboxVersion] =
    logAroundInfoQuery(
      (selectAllStatement(TIMEBOX_VERSION_TABLE_FRAG) ++ fr"where version = $version").query[TimeboxVersion].unique
    )(
      s"Finding timebox version with ${sp(version)}",
      found => s"Timebox version found ${sp(found)}"
    )

  override def findPaginatedByTimeboxId(
    timeboxId: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[TimeboxVersion]] =
    logAroundInfoQuery(
      TgFindPaginatedQuery[TimeboxVersion](
        pageQuery = request =>
          (selectAllStatement(TIMEBOX_VERSION_TABLE_FRAG) ++ fr"where timebox_id = $timeboxId" ++
            PaginationSql.orderBy(fr"version desc") ++
            PaginationSql.limitOffset(request)).query[TimeboxVersion].to[List],
        countQuery = (fr"select count(*) from" ++ TIMEBOX_VERSION_TABLE_FRAG ++
          fr"where timebox_id = $timeboxId").query[Long].unique
      ).run(page)
    )(
      s"Finding timebox versions of ${sp(timeboxId)} with ${sp(page)}",
      found => s"Timebox versions found ${sp(found)} for ${sp(timeboxId)}"
    )

  override def findLatestByTimeboxId(timeboxId: UUID): ConnectionIO[TimeboxVersion] =
    logAroundInfoQuery(
      (selectAllStatement(TIMEBOX_VERSION_TABLE_FRAG) ++
        fr"where timebox_id = $timeboxId" ++ orderByDescLimitOne(fr"version")).query[TimeboxVersion].unique
    )(
      s"Finding latest timebox version for ${sp(timeboxId)}",
      found => s"Latest timebox version found ${sp(found)}"
    )

  override def findPlannedTasks(timeboxVersion: UUID): ConnectionIO[List[TaskVersion]] =
    logAroundInfoQuery(
      (fr"select tv.* from" ++ TASK_VERSION_TABLE_FRAG ++ fr"tv join" ++ TIMEBOX_VERSION_TASK_TABLE_FRAG ++
        fr"tvt on tvt.task_version = tv.version where tvt.timebox_version = $timeboxVersion order by tv.version")
        .query[TaskVersion]
        .to[List]
    )(
      s"Finding planned tasks for ${sp(timeboxVersion)}",
      found => s"Planned tasks found ${sp(found)} for ${sp(timeboxVersion)}"
    )

}

object TimeboxVersionRepoImpl {
  val TIMEBOX_VERSION_TABLE: String = "timebox_version"
  val TIMEBOX_VERSION_TABLE_FRAG: Fragment = Fragment.const(TIMEBOX_VERSION_TABLE)
  val TIMEBOX_VERSION_TASK_TABLE: String = "timebox_version_task"
  val TIMEBOX_VERSION_TASK_TABLE_FRAG: Fragment = Fragment.const(TIMEBOX_VERSION_TASK_TABLE)
}
