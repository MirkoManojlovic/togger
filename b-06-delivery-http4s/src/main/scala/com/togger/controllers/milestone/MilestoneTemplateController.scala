package com.togger.controllers.milestone

import com.togger.common.TgClock
import com.togger.common.TgError._
import com.togger.common.TgJsonSuccess
import com.togger.controllers.common.PageRequestValidation
import com.togger.controllers.milestone.MilestoneTemplateDtoMapper.toDto
import com.togger.controllers.milestone.MilestoneTemplateDtoMapper.toVersionDto
import com.togger.dto.FindMilestoneTemplateResp
import com.togger.milestone.MilestoneTemplateEndpoints
import com.togger.milestonetemplate.MilestoneTemplate
import com.togger.milestonetemplate.MilestoneTemplateService
import com.togger.milestonetemplate.MilestoneTemplateVersion

import java.util.UUID

class MilestoneTemplateController(
  templateService: MilestoneTemplateService,
  endpoints: MilestoneTemplateEndpoints,
  tgClock: TgClock
) {

  val save = endpoints.saveEndpoint.serverLogic { auth => dto =>
    val now = tgClock.currentInstant
    val templateId = UUID.randomUUID()
    val template = MilestoneTemplate(templateId, dto.projectId, auth.body.get.id, now)
    val firstVersion = MilestoneTemplateVersion(UUID.randomUUID(), templateId, dto.name, dto.description, now)
    templateService.create(template, firstVersion, dto.stepTemplateIds).map {
      case Right(full) => TgJsonSuccess.justBodyRight(toDto(full))
      case Left(error) => error.toBadRequestLeft[FindMilestoneTemplateResp]
    }
  }

  val findById = endpoints.findByIdEndpoint.serverLogic { _ => id =>
    templateService.findById(id).map {
      case Right(full) => TgJsonSuccess.justBodyRight(toDto(full))
      case Left(error) => error.toBadRequestLeft[FindMilestoneTemplateResp]
    }
  }

  val update = endpoints.updateEndpoint.serverLogic { _ => dto =>
    templateService.update(dto.id, dto.name, dto.description, dto.stepTemplateIds, tgClock.currentInstant).map {
      case Right(full) => TgJsonSuccess.justBodyRight(toDto(full))
      case Left(error) => error.toBadRequestLeft[FindMilestoneTemplateResp]
    }
  }

  val findByProjectId = endpoints.findByProjectIdEndpoint.serverLogic { _ => request =>
    val (projectId, pageRequest) = request
    PageRequestValidation.withPage(pageRequest) { page =>
      templateService
        .findPaginatedByProjectId(projectId, page)
        .map(templatesPage => TgJsonSuccess.justBodyRight(templatesPage.mapItem(toDto)))
    }
  }

  val listVersions = endpoints.listVersionsEndpoint.serverLogic { _ => request =>
    val (templateId, pageRequest) = request
    PageRequestValidation.withPage(pageRequest) { page =>
      templateService
        .listVersions(templateId, page)
        .map(versionsPage => TgJsonSuccess.justBodyRight(versionsPage.mapItem(toVersionDto)))
    }
  }

  val delete = endpoints.deleteByIdEndpoint.serverLogic { _ => id =>
    templateService.delete(id).map {
      case Right(_)    => TgJsonSuccess.justMessageRight("Milestone template deleted successfully")
      case Left(error) => error.toBadRequestLeft[TgJsonSuccess.TgEmptyBody]
    }
  }
}
