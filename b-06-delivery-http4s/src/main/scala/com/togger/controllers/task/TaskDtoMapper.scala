package com.togger.controllers.task

import com.togger.dto.{FindTaskResp1, TaskVersionDto}
import com.togger.task.{Task, TaskVersion}

import java.util.UUID

object TaskDtoMapper {

  def toDto(task: Task, version: TaskVersion, taskLabelIds: Seq[UUID] = Seq.empty): FindTaskResp1 =
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
      milestone = version.milestone,
      startDate = version.startDate,
      dueDate = version.dueDate,
      endDate = version.endDate,
      taskLabelIds = taskLabelIds,
      archived = task.archived
    )

  def toVersionDto(version: TaskVersion): TaskVersionDto =
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
      milestone = version.milestone,
      startDate = version.startDate,
      dueDate = version.dueDate,
      endDate = version.endDate
    )

}
