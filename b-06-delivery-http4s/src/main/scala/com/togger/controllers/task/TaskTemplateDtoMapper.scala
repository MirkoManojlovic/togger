package com.togger.controllers.task

import com.togger.dto.{FindTaskTemplateResp, TaskTemplateVersionDto}
import com.togger.tasktemplate.{TaskTemplateFull, TaskTemplateVersion}

object TaskTemplateDtoMapper {

  def toDto(full: TaskTemplateFull): FindTaskTemplateResp =
    FindTaskTemplateResp(
      id = full.template.id,
      projectId = full.template.projectId,
      name = full.latestVersion.name,
      body = full.latestVersion.body,
      taskLabelId = full.template.taskLabelId,
      taskLabelName = full.taskLabelName,
      latestVersion = full.latestVersion.version,
      updatedAt = full.latestVersion.createdAt
    )

  def toVersionDto(version: TaskTemplateVersion): TaskTemplateVersionDto =
    TaskTemplateVersionDto(
      version = version.version,
      taskTemplateId = version.taskTemplateId,
      name = version.name,
      body = version.body,
      createdAt = version.createdAt
    )
}
