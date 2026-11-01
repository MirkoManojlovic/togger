package com.togger.controllers.timebox

import com.togger.controllers.task.TaskDtoMapper
import com.togger.dto.FindTimeboxResp
import com.togger.dto.TimeboxVersionDto
import com.togger.timebox.TimeboxVersionWithTasks
import com.togger.timebox.TimeboxWithOneVersion

import java.util.UUID

object TimeboxDtoMapper {

  def toVersionDto(
    versionWithTasks: TimeboxVersionWithTasks,
    milestoneOf: UUID => Option[UUID] = _ => None
  ): TimeboxVersionDto = {
    val version = versionWithTasks.version
    TimeboxVersionDto(
      version = version.version,
      timeboxId = version.timeboxId,
      startDate = version.startDate,
      endDate = version.endDate,
      description = version.description,
      createdAt = version.createdAt,
      changeComment = version.changeComment,
      tasks = versionWithTasks.tasks.map(TaskDtoMapper.toVersionDto(_, milestoneOf))
    )
  }

  def toDto(timeboxWithVersion: TimeboxWithOneVersion, milestoneOf: UUID => Option[UUID] = _ => None): FindTimeboxResp =
    FindTimeboxResp(
      id = timeboxWithVersion.timebox.id,
      projectId = timeboxWithVersion.timebox.projectId,
      createdAt = timeboxWithVersion.timebox.createdAt,
      latestVersion = toVersionDto(timeboxWithVersion.version, milestoneOf)
    )

}
