package com.togger.controllers.milestone

import com.togger.common.TgError._
import com.togger.common.{TgClock, TgJsonSuccess}
import com.togger.controllers.common.PageRequestValidation
import com.togger.controllers.milestone.MilestoneStepTemplateDtoMapper.{toDto, toVersionDto}
import com.togger.dto.FindMilestoneStepTemplateResp
import com.togger.milestone.MilestoneStepTemplateEndpoints
import com.togger.milestonesteptemplate.{
  MilestoneStepTemplate,
  MilestoneStepTemplateService,
  MilestoneStepTemplateVersion
}

import java.util.UUID

class MilestoneStepTemplateController(
  templateService: MilestoneStepTemplateService,
  endpoints: MilestoneStepTemplateEndpoints,
  tgClock: TgClock
) {

  val save = endpoints.saveEndpoint.serverLogic { auth => dto =>
    val now = tgClock.currentInstant
    val templateId = UUID.randomUUID()
    val template = MilestoneStepTemplate(templateId, dto.projectId, auth.body.get.id, now)
    val firstVersion = MilestoneStepTemplateVersion(UUID.randomUUID(), templateId, dto.name, dto.description, now)
    templateService.create(template, firstVersion).map(full => TgJsonSuccess.justBodyRight(toDto(full)))
  }

  val findById = endpoints.findByIdEndpoint.serverLogic { auth => id =>
    templateService.findById(id).map {
      case Right(full) => TgJsonSuccess.justBodyRight(toDto(full))
      case Left(error) => error.toBadRequestLeft[FindMilestoneStepTemplateResp]
    }
  }

  val update = endpoints.updateEndpoint.serverLogic { auth => dto =>
    templateService.update(dto.id, dto.name, dto.description, tgClock.currentInstant).map {
      case Right(full) => TgJsonSuccess.justBodyRight(toDto(full))
      case Left(error) => error.toBadRequestLeft[FindMilestoneStepTemplateResp]
    }
  }

  val findByProjectId = endpoints.findByProjectIdEndpoint.serverLogic { auth => request =>
    val (projectId, pageRequest) = request
    PageRequestValidation.withPage(pageRequest) { page =>
      templateService
        .findPaginatedByProjectId(projectId, page)
        .map(templatesPage => TgJsonSuccess.justBodyRight(templatesPage.mapItem(toDto)))
    }
  }

  val listVersions = endpoints.listVersionsEndpoint.serverLogic { auth => request =>
    val (templateId, pageRequest) = request
    PageRequestValidation.withPage(pageRequest) { page =>
      templateService
        .listVersions(templateId, page)
        .map(versionsPage => TgJsonSuccess.justBodyRight(versionsPage.mapItem(toVersionDto)))
    }
  }

  val delete = endpoints.deleteByIdEndpoint.serverLogic { auth => id =>
    templateService.delete(id).map {
      case Right(_)    => TgJsonSuccess.justMessageRight("Milestone step template deleted successfully")
      case Left(error) => error.toBadRequestLeft[TgJsonSuccess.TgEmptyBody]
    }
  }
}
