package com.togger.ui.task

import com.togger.dto.{FindTaskRespFull, UpdateTaskReq, UpdateTaskReq1}
import com.togger.ui.task.bulk.{BulkLookups, BulkTask}

import java.time.Instant

/** Lets the popups and the editor of bulk add work on the tasks of the table. */
object ShowAllTasksBulkMapping {

  /** The due date of an existing task was chosen, so it is marked manual: moving the start date only moves it when it
    * would end up before the start.
    */
  def toBulkTask(dto: FindTaskRespFull): BulkTask =
    BulkTask(
      key = dto.id,
      name = dto.name,
      priority = dto.priority,
      statusId = Some(dto.taskStatus.id),
      startDate = dto.startDate,
      dueDate = dto.dueDate,
      endDate = dto.endDate,
      assigneeId = dto.assignedTo.map(_.id),
      labelIds = dto.taskLabels.map(_.id).toSet,
      milestoneId = dto.milestone,
      dueDateManual = true
    )

  /** The task with the edited attributes. What the lookups can't resolve (an unknown status or assignee) keeps its old
    * value.
    */
  def applyTo(dto: FindTaskRespFull, edited: BulkTask, lookups: BulkLookups): FindTaskRespFull =
    dto.copy(
      priority = edited.priority,
      taskStatus = edited.statusId.flatMap(id => lookups.statuses.find(_.id == id)).getOrElse(dto.taskStatus),
      assignedTo = edited.assigneeId match {
        case None     => None
        case Some(id) => lookups.members.find(_.id == id).orElse(dto.assignedTo.filter(_.id == id))
      },
      taskLabels = lookups.labels.filter(label => edited.labelIds.contains(label.id)),
      milestone = edited.milestoneId,
      startDate = edited.startDate,
      dueDate = edited.dueDate
    )

  /** Carries every attribute of the task, the labels included: the backend replaces a task's labels with the ones sent.
    */
  def toUpdateRequest(dto: FindTaskRespFull, now: Instant): UpdateTaskReq =
    UpdateTaskReq(
      task = UpdateTaskReq1(
        id = dto.id,
        projectId = dto.projectId,
        name = dto.name,
        description = dto.description,
        assignedTo = dto.assignedTo.map(_.id),
        createdBy = dto.createdBy.id,
        taskStatusId = dto.taskStatus.id,
        createdAt = now,
        updatedAt = now,
        priority = dto.priority,
        milestone = dto.milestone,
        startDate = Some(dto.startDate),
        dueDate = Some(dto.dueDate),
        endDate = dto.endDate,
        taskLabelIds = dto.taskLabels.map(_.id),
        archived = dto.archived
      ),
      linksForUpdate = Seq.empty,
      linksToCreate = Seq.empty,
      linksToDelete = Seq.empty
    )
}
