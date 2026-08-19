package com.togger.controllers.task

import com.togger.dto.{FindTaskResp1, TaskVersionDto}
import com.togger.task.{Task, TaskVersion}

object TaskDtoMapper {

  def toDto(task: Task, version: TaskVersion): FindTaskResp1 =
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
      latestVersion = version.version
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
      updatedAt = version.updatedAt
    )

}
