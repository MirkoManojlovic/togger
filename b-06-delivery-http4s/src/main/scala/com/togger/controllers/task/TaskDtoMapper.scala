package com.togger.controllers.task

import com.togger.dto.FindTaskResp1
import com.togger.dto.TaskVersionDto
import com.togger.task.Task
import com.togger.task.TaskVersion

import java.util.UUID

object TaskDtoMapper {

  /** `milestoneOf` gives the milestone of a step: the milestone of a task is the one of its step. */
  def toDto(
    task: Task,
    version: TaskVersion,
    taskLabelIds: Seq[UUID] = Seq.empty,
    milestoneOf: UUID => Option[UUID] = _ => None
  ): FindTaskResp1 =
    FindTaskResp1(
      id = task.id,
      projectId = task.projectId,
      name = version.name,
      description = version.description,
      assignedTo = version.assignedTo,
      createdBy = task.createdBy,
      taskStatusId = version.taskStatusId,
      createdAt = task.createdAt,
      updatedAt = version.updatedAt,
      latestVersion = version.version,
      priority = version.priority,
      milestone = version.milestoneStepId.flatMap(milestoneOf),
      milestoneStepId = version.milestoneStepId,
      startDate = version.startDate,
      dueDate = version.dueDate,
      endDate = version.endDate,
      taskLabelIds = taskLabelIds,
      archived = task.archived
    )

  def toVersionDto(version: TaskVersion, milestoneOf: UUID => Option[UUID] = _ => None): TaskVersionDto =
    TaskVersionDto(
      version = version.version,
      taskId = version.taskId,
      name = version.name,
      description = version.description,
      assignedTo = version.assignedTo,
      closedBy = version.closedBy,
      taskStatusId = version.taskStatusId,
      createdAt = version.createdAt,
      updatedAt = version.updatedAt,
      priority = version.priority,
      milestone = version.milestoneStepId.flatMap(milestoneOf),
      milestoneStepId = version.milestoneStepId,
      startDate = version.startDate,
      dueDate = version.dueDate,
      endDate = version.endDate
    )

}
