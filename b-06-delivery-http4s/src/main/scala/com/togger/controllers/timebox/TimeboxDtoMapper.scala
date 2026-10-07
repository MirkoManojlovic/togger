package com.togger.controllers.timebox

import com.togger.controllers.task.TaskDtoMapper
import com.togger.dto.{FindTimeboxResp, TimeboxVersionDto}
import com.togger.timebox.{TimeboxVersionWithTasks, TimeboxWithOneVersion}

object TimeboxDtoMapper {

  def toVersionDto(versionWithTasks: TimeboxVersionWithTasks): TimeboxVersionDto = {
    val version = versionWithTasks.version
    TimeboxVersionDto(
      version = version.version,
      timeboxId = version.timeboxId,
      startDate = version.startDate,
      endDate = version.endDate,
      description = version.description,
      createdAt = version.createdAt,
      tasks = versionWithTasks.tasks.map(TaskDtoMapper.toVersionDto)
    )
  }

  def toDto(timeboxWithVersion: TimeboxWithOneVersion): FindTimeboxResp =
    FindTimeboxResp(
      id = timeboxWithVersion.timebox.id,
      projectId = timeboxWithVersion.timebox.projectId,
      createdAt = timeboxWithVersion.timebox.createdAt,
      latestVersion = toVersionDto(timeboxWithVersion.version)
    )

}
