package com.togger.task

import com.togger.account.Account
import com.togger.account.AccountRepoImpl.passwordCodec
import com.togger.db.Util._
import com.togger.logging.TgLogging
import com.togger.task.TaskRepoImpl.TASK_TABLE_FRAG
import com.togger.task.TaskVersionRepoImpl.TASK_VERSION_TABLE_FRAG
import com.togger.taskStatus.TaskStatusRepoImpl.TASK_STATUS_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.fragment.Fragment

import java.util.UUID

class TaskVersionRepoImpl extends TaskVersionRepo with TgLogging {

  private lazy val returnFields = List(
    "version",
    "task_id",
    "name",
    "description",
    "assigned_to",
    "closed_by",
    "task_status_id",
    "milestone_id",
    "created_at",
    "updated_at"
  )

  // Latest task_version row per task, correlated on the outer `task` row via LATERAL.
  private def latestVersionLateral: Fragment =
    fr"join lateral (select * from" ++ TASK_VERSION_TABLE_FRAG ++
      fr"where task_id = task.id order by version desc limit 1) tv on true"

  private def toTaskFull(
    row: (Task, TaskVersion, TaskStatus, Account, Option[Account], Option[Account])
  ): TaskFull = {
    val (task, version, status, createdBy, assignedToOpt, closedByOpt) = row
    TaskFull(
      id = task.id,
      projectId = task.projectId,
      name = version.name,
      description = version.description,
      assignedTo = assignedToOpt,
      createdBy = createdBy,
      closedBy = closedByOpt,
      taskStatus = status,
      milestone = version.milestone,
      createdAt = task.createdAt,
      updatedAt = version.updatedAt,
      latestVersion = version.version
    )
  }

  override def insert(taskVersion: TaskVersion): ConnectionIO[TaskVersion] = {
    import taskVersion._
    logAroundInfoQuery(
      insertStatement(TASK_VERSION_TABLE_FRAG)(fr"""
        (task_id, name, description, assigned_to, closed_by, task_status_id, milestone_id, created_at, updated_at)
        values ($taskId, $name, $description, $assignedTo, $closedBy, $taskStatusId, $milestone, $createdAt, $updatedAt)
      """).update
        .withUniqueGeneratedKeys[TaskVersion](returnFields: _*)
    )(s"Inserting ${sp(taskVersion)}", taskVersion => s"Task version inserted ${sp(taskVersion)}")
  }

  override def insertWithId(taskVersion: TaskVersion): ConnectionIO[TaskVersion] = {
    import taskVersion._
    logAroundInfoQuery(
      insertStatement(TASK_VERSION_TABLE_FRAG)(fr"""
        (version, task_id, name, description, assigned_to, closed_by, task_status_id, milestone_id, created_at, updated_at)
        values ($version, $taskId, $name, $description, $assignedTo, $closedBy, $taskStatusId, $milestone, $createdAt, $updatedAt)
      """).update
        .withUniqueGeneratedKeys[TaskVersion](returnFields: _*)
    )(
      s"Inserting ${sp(taskVersion)} with existing version",
      taskVersion => s"Task version inserted with existing version ${sp(taskVersion)}"
    )
  }

  override def findByVersion(version: UUID): ConnectionIO[TaskVersion] =
    logAroundInfoQuery(
      (selectAllStatement(TASK_VERSION_TABLE_FRAG) ++ fr"where version = $version").query[TaskVersion].unique
    )(
      s"Finding task version with ${sp(version)}",
      taskVersion => s"Task version found ${sp(taskVersion)}"
    )

  override def findByTaskId(taskId: UUID): ConnectionIO[List[TaskVersion]] =
    logAroundInfoQuery(
      (selectAllStatement(TASK_VERSION_TABLE_FRAG) ++
        fr"where task_id = $taskId order by version desc").query[TaskVersion].to[List]
    )(
      s"Finding task versions for ${sp(taskId)}",
      taskVersions => s"Task versions found ${sp(taskVersions)} for ${sp(taskId)}"
    )

  override def findLatestByTaskId(taskId: UUID): ConnectionIO[TaskVersion] =
    logAroundInfoQuery(
      (selectAllStatement(TASK_VERSION_TABLE_FRAG) ++
        fr"where task_id = $taskId" ++ orderByDescLimitOne(fr"version")).query[TaskVersion].unique
    )(
      s"Finding latest task version for ${sp(taskId)}",
      taskVersion => s"Latest task version found ${sp(taskVersion)}"
    )

  override def updateContent(taskVersion: TaskVersion): ConnectionIO[TaskVersion] =
    logAroundInfoQuery(
      (fr"update" ++ TASK_VERSION_TABLE_FRAG ++ fr"""set
            name = ${taskVersion.name},
            description = ${taskVersion.description},
            assigned_to = ${taskVersion.assignedTo},
            closed_by = ${taskVersion.closedBy},
            task_status_id = ${taskVersion.taskStatusId},
            milestone_id = ${taskVersion.milestone},
            updated_at = ${taskVersion.updatedAt}
          where version = ${taskVersion.version}""").update
        .withUniqueGeneratedKeys[TaskVersion](returnFields: _*)
    )(s"Updating ${sp(taskVersion)}", taskVersion => s"${sp(taskVersion)} updated")

  override def deleteByVersion(version: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(
      (deleteStatement(TASK_VERSION_TABLE_FRAG) ++ fr"where version = $version").update.run
    )(
      s"Deleting task version with ${sp(version)}",
      numDeleted => s"Task version deleted ${sp(numDeleted)}"
    )

  override def countByTaskId(taskId: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(
      (countStatement(TASK_VERSION_TABLE_FRAG) ++ fr"where task_id = $taskId").query[Int].unique
    )(
      s"Counting task versions for ${sp(taskId)}",
      count => s"Task version count ${sp(count)} for ${sp(taskId)}"
    )

  override def findByIdWithLatest(taskId: UUID): ConnectionIO[TaskFull] =
    logAroundInfoQuery(
      (selectAllStatement(TASK_TABLE_FRAG) ++
        latestVersionLateral ++
        innerJoin(TASK_STATUS_TABLE_FRAG)(fr"tv.task_status_id = task_status.id") ++
        innerJoin(fr"account as a1")(fr"task.created_by = a1.id") ++
        leftJoin(fr"account as a2")(fr"tv.assigned_to = a2.id") ++
        leftJoin(fr"account as a3")(fr"tv.closed_by = a3.id") ++
        fr"where task.id = $taskId")
        .query[(Task, TaskVersion, TaskStatus, Account, Option[Account], Option[Account])]
        .map(toTaskFull)
        .unique
    )(
      s"Finding task with latest version, task status, createdBy and assignedTo ${sp(taskId)}",
      taskFull => s"Task ${sp(taskFull)}"
    )

  override def findByProjectIdWithLatest(projectId: UUID): ConnectionIO[Seq[TaskFull]] =
    logAroundInfoQuery(
      (selectAllStatement(TASK_TABLE_FRAG) ++
        latestVersionLateral ++
        innerJoin(TASK_STATUS_TABLE_FRAG)(fr"tv.task_status_id = task_status.id") ++
        innerJoin(fr"account as a1")(fr"task.created_by = a1.id") ++
        leftJoin(fr"account as a2")(fr"tv.assigned_to = a2.id") ++
        leftJoin(fr"account as a3")(fr"tv.closed_by = a3.id") ++
        fr"where task.project_id = $projectId")
        .query[(Task, TaskVersion, TaskStatus, Account, Option[Account], Option[Account])]
        .map(toTaskFull)
        .to[Seq]
    )(
      s"Finding all tasks with latest version by ${sp(projectId)}",
      tasks => s"Tasks found ${sp(tasks)} for ${sp(projectId)}"
    )

  override def findAllTaskIdsByAssigneeId(accountId: UUID): ConnectionIO[List[UUID]] =
    logAroundInfoQuery(
      (fr"select task.id from" ++ TASK_TABLE_FRAG ++
        latestVersionLateral ++
        fr"where tv.assigned_to = $accountId").query[UUID].to[List]
    )(
      s"Finding task ids for ${sp(accountId)}",
      taskIds => s"Task ids found ${sp(taskIds)}"
    )

}

object TaskVersionRepoImpl {
  val TASK_VERSION_TABLE: String = "task_version"
  val TASK_VERSION_TABLE_FRAG: Fragment = Fragment.const(TASK_VERSION_TABLE)
}
