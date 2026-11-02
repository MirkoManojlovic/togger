package com.togger.controllers.task

import com.togger.common.TgClock
import com.togger.common.TgError._
import com.togger.common.TgJsonSuccess
import com.togger.controllers.common.PageRequestValidation
import com.togger.controllers.task.TaskTemplateDtoMapper.toDto
import com.togger.controllers.task.TaskTemplateDtoMapper.toVersionDto
import com.togger.dto.FindTaskTemplateResp
import com.togger.task.TaskTemplateEndpoints
import com.togger.tasktemplate.TaskTemplate
import com.togger.tasktemplate.TaskTemplateService
import com.togger.tasktemplate.TaskTemplateVersion

import java.util.UUID

class TaskTemplateController(
  taskTemplateService: TaskTemplateService,
  taskTemplateEndpoints: TaskTemplateEndpoints,
  tgClock: TgClock
) {

  import taskTemplateEndpoints._

  val save = saveEndpoint.serverLogic { auth => dto =>
    val now = tgClock.currentInstant
    val templateId = UUID.randomUUID()
    val template = TaskTemplate(templateId, dto.projectId, dto.taskLabelId, auth.body.get.id, now)
    val firstVersion = TaskTemplateVersion(UUID.randomUUID(), templateId, dto.name, dto.body, now)
    taskTemplateService.create(template, firstVersion).map {
      case Right(full) => TgJsonSuccess.justBodyRight(toDto(full))
      case Left(error) => error.toBadRequestLeft[FindTaskTemplateResp]
    }
  }

  val findById = findByIdEndpoint.serverLogic { _ => id =>
    taskTemplateService.findById(id).map {
      case Right(full) => TgJsonSuccess.justBodyRight(toDto(full))
      case Left(error) => error.toBadRequestLeft[FindTaskTemplateResp]
    }
  }

  val update = updateEndpoint.serverLogic { _ => dto =>
    taskTemplateService.update(dto.id, dto.name, dto.body, dto.taskLabelId, tgClock.currentInstant).map {
      case Right(full) => TgJsonSuccess.justBodyRight(toDto(full))
      case Left(error) => error.toBadRequestLeft[FindTaskTemplateResp]
    }
  }

  val findByProjectId = findByProjectIdEndpoint.serverLogic { _ => request =>
    val (projectId, pageRequest) = request
    PageRequestValidation.withPage(pageRequest) { page =>
      taskTemplateService
        .findPaginatedByProjectId(projectId, page)
        .map(templatesPage => TgJsonSuccess.justBodyRight(templatesPage.mapItem(toDto)))
    }
  }

  val findByLabel = findByLabelEndpoint.serverLogic { _ => labelId =>
    taskTemplateService
      .findByLabelId(labelId)
      .map(template => TgJsonSuccess.justBodyRight(template.map(toDto)))
  }

  val listVersions = listVersionsEndpoint.serverLogic { _ => request =>
    val (templateId, pageRequest) = request
    PageRequestValidation.withPage(pageRequest) { page =>
      taskTemplateService
        .listVersions(templateId, page)
        .map(versionsPage => TgJsonSuccess.justBodyRight(versionsPage.mapItem(toVersionDto)))
    }
  }

  val delete = deleteByIdEndpoint.serverLogic { _ => id =>
    taskTemplateService.delete(id).map {
      case Right(_)    => TgJsonSuccess.justMessageRight("Task template deleted successfully")
      case Left(error) => error.toBadRequestLeft[TgJsonSuccess.TgEmptyBody]
    }
  }
}
