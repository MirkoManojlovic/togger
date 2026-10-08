package com.togger.task

import cats.data.NonEmptyList
import com.togger.common.{DbPageRequest, PaginatedResponse}
import com.togger.account.Account
import com.togger.account.AccountRepoImpl.passwordCodec
import com.togger.common.{DbPageRequest, PaginatedResponse, TgIds}
import com.togger.db.Util._
import com.togger.db.{PaginationSql, TgFindPaginatedQuery}
import com.togger.dto.TaskFilter
import com.togger.logging.TgLogging
import com.togger.task.TaskLabelAssignmentRepoImpl.TASK_LABEL_ASSIGNMENT_TABLE_FRAG
import com.togger.task.TaskRepoImpl.TASK_TABLE_FRAG
import com.togger.task.TaskVersionRepoImpl.TASK_VERSION_TABLE_FRAG
import com.togger.taskStatus.TaskStatusRepoImpl.TASK_STATUS_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.{ConnectionIO, Fragments, Meta}
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.fragment.Fragment

import java.util.UUID

class TaskVersionRepoImpl extends TaskVersionRepo with TgLogging {
  import TaskVersionRepoImpl.taskPriorityCodec

  private lazy val returnFields = List(
    "version",
    "task_id",
    "name",
    "description",
    "assigned_to",
    "closed_by",
    "task_status_id",
    "priority",
    "milestone_id",
    "start_date",
    "due_date",
    "end_date",
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
      priority = version.priority,
      milestone = version.milestone,
      startDate = version.startDate,
      dueDate = version.dueDate,
      endDate = version.endDate,
      createdAt = task.createdAt,
      updatedAt = version.updatedAt,
      latestVersion = version.version
    )
  }

  override def insert(taskVersion: TaskVersion): ConnectionIO[TaskVersion] = {
    import taskVersion._
    logAroundInfoQuery(
      insertStatement(TASK_VERSION_TABLE_FRAG)(fr"""
        (task_id, name, description, assigned_to, closed_by, task_status_id, priority, milestone_id, start_date, due_date, end_date, created_at, updated_at)
        values ($taskId, $name, $description, $assignedTo, $closedBy, $taskStatusId, $priority, $milestone, $startDate, $dueDate, $endDate, $createdAt, $updatedAt)
      """).update
        .withUniqueGeneratedKeys[TaskVersion](returnFields: _*)
    )(s"Inserting ${sp(taskVersion)}", taskVersion => s"Task version inserted ${sp(taskVersion)}")
  }

  override def insertWithId(taskVersion: TaskVersion): ConnectionIO[TaskVersion] = {
    import taskVersion._
    logAroundInfoQuery(
      insertStatement(TASK_VERSION_TABLE_FRAG)(fr"""
        (version, task_id, name, description, assigned_to, closed_by, task_status_id, priority, milestone_id, start_date, due_date, end_date, created_at, updated_at)
        values ($version, $taskId, $name, $description, $assignedTo, $closedBy, $taskStatusId, $priority, $milestone, $startDate, $dueDate, $endDate, $createdAt, $updatedAt)
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

  override def findPaginatedByTaskId(
    taskId: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[TaskVersion]] =
    logAroundInfoQuery(
      TgFindPaginatedQuery[TaskVersion](
        pageQuery = request =>
          (selectAllStatement(TASK_VERSION_TABLE_FRAG) ++ fr"where task_id = $taskId" ++
            PaginationSql.orderBy(fr"version desc") ++
            PaginationSql.limitOffset(request)).query[TaskVersion].to[List],
        countQuery = (fr"select count(*) from" ++ TASK_VERSION_TABLE_FRAG ++
          fr"where task_id = $taskId").query[Long].unique
      ).run(page)
    )(
      s"Finding task versions of ${sp(taskId)} with ${sp(page)}",
      found => s"Task versions found ${sp(found)} for ${sp(taskId)}"
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
            priority = ${taskVersion.priority},
            milestone_id = ${taskVersion.milestone},
            start_date = ${taskVersion.startDate},
            due_date = ${taskVersion.dueDate},
            end_date = ${taskVersion.endDate},
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

  override def findAllByProjectIdWithLatest(projectId: UUID): ConnectionIO[List[TaskFull]] =
    logAroundInfoQuery(
      (selectAllStatement(TASK_TABLE_FRAG) ++
        latestVersionLateral ++
        innerJoin(TASK_STATUS_TABLE_FRAG)(fr"tv.task_status_id = task_status.id") ++
        innerJoin(fr"account as a1")(fr"task.created_by = a1.id") ++
        leftJoin(fr"account as a2")(fr"tv.assigned_to = a2.id") ++
        leftJoin(fr"account as a3")(fr"tv.closed_by = a3.id") ++
        fr"where task.project_id = $projectId" ++ PaginationSql.orderBy(fr"tv.updated_at desc", fr"task.id"))
        .query[(Task, TaskVersion, TaskStatus, Account, Option[Account], Option[Account])]
        .map(toTaskFull)
        .to[List]
    )(
      s"Finding all tasks with latest version by ${sp(projectId)}",
      tasks => s"Tasks found ${sp(tasks)} for ${sp(projectId)}"
    )

  override def findPaginatedByProjectId(
    projectId: UUID,
    filter: TaskFilter,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[TaskFull]] =
    logAroundInfoQuery(
      TgFindPaginatedQuery[TaskFull](
        pageQuery = findPageWithLatest(projectId, filter, _),
        countQuery = (fr"select count(*)" ++ filteredTasks(projectId, filter)).query[Long].unique
      ).run(page)
    )(
      s"Finding tasks of ${sp(projectId)} with ${sp(filter)} and ${sp(page)}",
      tasks => s"Tasks found ${sp(tasks)} for ${sp(projectId)}"
    )

  // The limit applies to task ids, so the joins below can't multiply or cut the rows of a page. Order is total: the
  // latest version's update time, then the task id.
  private def findPageWithLatest(
    projectId: UUID,
    filter: TaskFilter,
    page: DbPageRequest
  ): ConnectionIO[List[TaskFull]] =
    (fr"with page_ids as (select task.id as id, tv.updated_at as sort_key" ++ filteredTasks(projectId, filter) ++
      PaginationSql.orderBy(fr"tv.updated_at desc", fr"task.id") ++ PaginationSql.limitOffset(page) ++ fr")" ++
      fr"select task.*, tv.*, task_status.*, a1.*, a2.*, a3.* from page_ids" ++
      innerJoin(TASK_TABLE_FRAG)(fr"task.id = page_ids.id") ++
      latestVersionLateral ++
      innerJoin(TASK_STATUS_TABLE_FRAG)(fr"tv.task_status_id = task_status.id") ++
      innerJoin(fr"account as a1")(fr"task.created_by = a1.id") ++
      leftJoin(fr"account as a2")(fr"tv.assigned_to = a2.id") ++
      leftJoin(fr"account as a3")(fr"tv.closed_by = a3.id") ++
      PaginationSql.orderBy(fr"page_ids.sort_key desc", fr"task.id"))
      .query[(Task, TaskVersion, TaskStatus, Account, Option[Account], Option[Account])]
      .map(toTaskFull)
      .to[List]

  // Shared by the page and the count query. Label filtering uses exists, so a task with several labels stays one row.
  private def filteredTasks(projectId: UUID, filter: TaskFilter): Fragment =
    fr"from" ++ TASK_TABLE_FRAG ++ latestVersionLateral ++
      Fragments.whereAndOpt(
        Some(fr"task.project_id = $projectId"),
        NonEmptyList.fromList(filter.statusIds).map(Fragments.in(fr"tv.task_status_id", _)),
        NonEmptyList.fromList(filter.priorities).map(Fragments.in(fr"tv.priority", _)),
        NonEmptyList.fromList(filter.labelIds).map(hasAnyLabel),
        NonEmptyList.fromList(filter.assigneeIds).map(inOrNull(fr"tv.assigned_to", _, TgIds.UNASSIGNED_UUID)),
        NonEmptyList.fromList(filter.milestoneIds).map(inOrNull(fr"tv.milestone_id", _, TgIds.NO_MILESTONE_UUID)),
        filter.search.map(_.trim).filter(_.nonEmpty).map(matchesText)
      )

  private def hasAnyLabel(labelIds: NonEmptyList[UUID]): Fragment =
    fr"exists (select 1 from" ++ TASK_LABEL_ASSIGNMENT_TABLE_FRAG ++
      fr"where task_label_assignment.task_id = task.id and" ++
      Fragments.in(fr"task_label_assignment.task_label_id", labelIds) ++ fr")"

  private def inOrNull(column: Fragment, ids: NonEmptyList[UUID], nullMarker: UUID): Fragment =
    NonEmptyList.fromList(ids.filterNot(_ == nullMarker)) match {
      case Some(realIds) if ids.exists(_ == nullMarker) =>
        fr"(" ++ Fragments.in(column, realIds) ++ fr"or" ++ column ++ fr"is null)"
      case Some(realIds) => Fragments.in(column, realIds)
      case None          => column ++ fr"is null"
    }

  // Postgres treats backslash as the LIKE escape by default, so the user's own %, _ and \ are escaped with it.
  private def matchesText(term: String): Fragment = {
    val pattern = "%" + term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
    fr"(tv.name ilike $pattern or tv.description ilike $pattern)"
  }

}

object TaskVersionRepoImpl {
  val TASK_VERSION_TABLE: String = "task_version"
  val TASK_VERSION_TABLE_FRAG: Fragment = Fragment.const(TASK_VERSION_TABLE)

  implicit val taskPriorityCodec: Meta[TaskPriority] = Meta[Int].imap(TaskPriority.withValue)(_.value)
}
