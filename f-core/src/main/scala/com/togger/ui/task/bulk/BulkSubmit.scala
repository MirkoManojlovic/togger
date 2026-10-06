package com.togger.ui.task.bulk

import com.togger.dto.{CreateTaskAndLinksReq, CreateTaskReq, CreateTasksBulkReq}
import com.togger.task.BulkTaskValidation

import java.time.Instant
import java.util.UUID

object BulkSubmit {

  /** Why the tasks cannot be created yet, if they cannot: what the user has to fix first. */
  def blocker(projectId: Option[UUID], tasks: Seq[BulkTask], hasParseIssues: Boolean): Option[String] =
    if (projectId.isEmpty) Some("Pick a project")
    else if (hasParseIssues) Some("Fix the lines marked above first")
    else if (tasks.isEmpty) Some("Add at least one task")
    else if (tasks.size > BulkTaskValidation.MaxTasks) Some(s"At most ${BulkTaskValidation.MaxTasks} tasks at once")
    else if (tasks.exists(_.statusId.isEmpty)) Some("The project has no task statuses")
    else None

  /** The request for the tasks in list order, or the reason there is none. Links are not part of a bulk add. */
  def request(
    projectId: Option[UUID],
    tasks: Seq[BulkTask],
    hasParseIssues: Boolean,
    createdBy: UUID,
    now: Instant
  ): Either[String, CreateTasksBulkReq] =
    blocker(projectId, tasks, hasParseIssues) match {
      case Some(reason) => Left(reason)
      case None =>
        Right(
          CreateTasksBulkReq(
            tasks.flatMap(task =>
              for {
                project <- projectId
                statusId <- task.statusId
              } yield CreateTaskAndLinksReq(
                task = CreateTaskReq(
                  projectId = project,
                  name = task.name,
                  description = "",
                  assignedTo = task.assigneeId,
                  createdBy = createdBy,
                  taskStatusId = statusId,
                  createdAt = now,
                  updatedAt = now,
                  priority = task.priority,
                  milestone = task.milestoneId,
                  startDate = task.startDate,
                  dueDate = task.dueDate,
                  taskLabelIds = task.labelIds.toSeq.sortBy(_.toString)
                ),
                links = Seq.empty
              )
            )
          )
        )
    }
}
