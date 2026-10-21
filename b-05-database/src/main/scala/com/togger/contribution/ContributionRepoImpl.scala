package com.togger.contribution

import com.togger.db.Util._
import com.togger.dto.ContributionsFilter
import com.togger.logentry.LogEntryRepoImpl.LOG_ENTRY_TABLE_FRAG
import com.togger.logentry.LogEntryVersionRepoImpl.{LOG_ENTRY_VERSION_TABLE_FRAG, logEntryStatusCodec}
import com.togger.milestone.MilestoneRepoImpl.MILESTONE_TABLE_FRAG
import com.togger.milestone.MilestoneVersionRepoImpl.{MILESTONE_VERSION_TABLE_FRAG, milestoneStatusCodec}
import com.togger.task.TaskRepoImpl.TASK_TABLE_FRAG
import com.togger.task.TaskVersionRepoImpl.latestVersionLateral
import com.togger.util.SplunkMacro.sp
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.{ConnectionIO, Fragment, Fragments}

import java.util.UUID

class ContributionRepoImpl extends ContributionRepo {

  private val entryDay: Fragment = fr"(log_entry.created_at at time zone 'UTC')::date"

  override def entryActivity(projectId: UUID, filter: ContributionsFilter): ConnectionIO[List[EntryActivityRow]] =
    logAroundInfoQuery(
      (fr"select log_entry.created_by, tv.milestone_id," ++ entryDay ++ fr", latest_entry.status, count(*) from" ++
        LOG_ENTRY_TABLE_FRAG ++ innerJoin(TASK_TABLE_FRAG)(fr"log_entry.task_id = task.id") ++ latestVersionLateral ++
        fr"join lateral (select status from" ++ LOG_ENTRY_VERSION_TABLE_FRAG ++
        fr"where log_entry_id = log_entry.id order by version desc limit 1) latest_entry on true" ++
        Fragments.whereAndOpt(
          Some(fr"task.project_id = $projectId"),
          filter.from.map(from => entryDay ++ fr">= $from"),
          filter.to.map(to => entryDay ++ fr"<= $to")
        ) ++ fr"group by log_entry.created_by, tv.milestone_id, 3, latest_entry.status")
        .query[EntryActivityRow]
        .to[List]
    )(
      s"Aggregating log entry activity of ${sp(projectId)} with ${sp(filter)}",
      rows => s"Log entry activity aggregated into ${sp(rows.size)} rows for ${sp(projectId)}"
    )

  override def tasksAssigned(projectId: UUID, filter: ContributionsFilter): ConnectionIO[List[TaskAssignedRow]] =
    logAroundInfoQuery(
      (fr"select tv.assigned_to, tv.milestone_id, count(*) from" ++ TASK_TABLE_FRAG ++ latestVersionLateral ++
        Fragments.whereAndOpt(
          Some(fr"task.project_id = $projectId"),
          Some(fr"tv.assigned_to is not null"),
          filter.from.map(from => fr"tv.start_date >= $from"),
          filter.to.map(to => fr"tv.start_date <= $to")
        ) ++ fr"group by tv.assigned_to, tv.milestone_id").query[TaskAssignedRow].to[List]
    )(
      s"Counting assigned tasks of ${sp(projectId)} with ${sp(filter)}",
      rows => s"Assigned tasks counted in ${sp(rows.size)} rows for ${sp(projectId)}"
    )

  override def tasksClosed(projectId: UUID, filter: ContributionsFilter): ConnectionIO[List[TaskClosedRow]] =
    logAroundInfoQuery(
      (fr"select tv.closed_by, tv.milestone_id, coalesce(tv.end_date <= tv.due_date, true), count(*) from" ++
        TASK_TABLE_FRAG ++ latestVersionLateral ++
        Fragments.whereAndOpt(
          Some(fr"task.project_id = $projectId"),
          Some(fr"tv.closed_by is not null"),
          filter.from.map(from => fr"tv.end_date >= $from"),
          filter.to.map(to => fr"tv.end_date <= $to")
        ) ++ fr"group by tv.closed_by, tv.milestone_id, 3").query[TaskClosedRow].to[List]
    )(
      s"Counting closed tasks of ${sp(projectId)} with ${sp(filter)}",
      rows => s"Closed tasks counted in ${sp(rows.size)} rows for ${sp(projectId)}"
    )

  override def milestoneHeaders(projectId: UUID): ConnectionIO[List[MilestoneHeaderRow]] =
    logAroundInfoQuery(
      (fr"select milestone.id, latest.name, latest.status, latest.start_date, latest.due_date from" ++
        MILESTONE_TABLE_FRAG ++ fr"join lateral (select * from" ++ MILESTONE_VERSION_TABLE_FRAG ++
        fr"where milestone_id = milestone.id order by version desc limit 1) latest on true" ++
        fr"where milestone.project_id = $projectId order by latest.due_date, milestone.id")
        .query[MilestoneHeaderRow]
        .to[List]
    )(
      s"Finding milestone headers of ${sp(projectId)}",
      rows => s"Milestone headers found ${sp(rows)} for ${sp(projectId)}"
    )

  override def milestoneTaskTotals(projectId: UUID): ConnectionIO[List[MilestoneTaskTotalsRow]] =
    logAroundInfoQuery(
      (fr"select tv.milestone_id, count(*), count(tv.end_date) from" ++ TASK_TABLE_FRAG ++ latestVersionLateral ++
        fr"where task.project_id = $projectId group by tv.milestone_id").query[MilestoneTaskTotalsRow].to[List]
    )(
      s"Counting tasks per milestone of ${sp(projectId)}",
      rows => s"Tasks per milestone counted in ${sp(rows.size)} rows for ${sp(projectId)}"
    )
}
