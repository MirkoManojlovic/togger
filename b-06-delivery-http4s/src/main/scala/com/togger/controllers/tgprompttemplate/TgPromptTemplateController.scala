package com.togger.controllers.tgprompttemplate

import com.togger.common.TgError._
import com.togger.common.{PaginatedResponse, TgClock, TgJsonSuccess}
import com.togger.controllers.common.PageRequestValidation
import com.togger.controllers.tgprompttemplate.TgPromptTemplateDtoMapper.{toDto, toVersionDto}
import com.togger.dto.{FindTgPromptTemplateResp, TgPromptTemplateVersionDto}
import com.togger.tgprompttemplate.{TgPromptTemplate, TgPromptTemplateEndpoints, TgPromptTemplateService, TgPromptTemplateVersion}

import java.util.UUID

class TgPromptTemplateController(
  tgPromptTemplateService: TgPromptTemplateService,
  tgPromptTemplateEndpoints: TgPromptTemplateEndpoints,
  tgClock: TgClock
) {

  import tgPromptTemplateEndpoints._

  val save = saveEndpoint.serverLogic { auth => dto =>
    val now = tgClock.currentInstant
    val promptId = UUID.randomUUID()
    val tgPromptTemplate = TgPromptTemplate(promptId, auth.body.get.id, now)
    val firstVersion = TgPromptTemplateVersion(UUID.randomUUID(), promptId, dto.title, dto.prompt, now)
    tgPromptTemplateService
      .create(tgPromptTemplate, firstVersion)
      .map(full => TgJsonSuccess.justBodyRight(toDto(full)))
  }

  val newVersion = newVersionEndpoint.serverLogic { auth => dto =>
    tgPromptTemplateService.saveNewVersion(auth.body.get.id, dto.id, dto.title, dto.prompt, tgClock.currentInstant).map {
      case Right(full) => TgJsonSuccess.justBodyRight(toDto(full))
      case Left(error) => error.toBadRequestLeft[FindTgPromptTemplateResp]
    }
  }

  val findAll = findAllEndpoint.serverLogic { auth => pageRequest =>
    PageRequestValidation.withPage(pageRequest) { page =>
      tgPromptTemplateService
        .findPaginated(auth.body.get.id, page)
        .map(promptsPage => TgJsonSuccess.justBodyRight(promptsPage.mapItem(toDto)))
    }
  }

  val findById = findByIdEndpoint.serverLogic { auth => id =>
    tgPromptTemplateService.findById(auth.body.get.id, id).map {
      case Right(full) => TgJsonSuccess.justBodyRight(toDto(full))
      case Left(error) => error.toBadRequestLeft[FindTgPromptTemplateResp]
    }
  }

  val listVersions = listVersionsEndpoint.serverLogic { auth => request =>
    val (promptId, pageRequest) = request
    PageRequestValidation.withPage(pageRequest) { page =>
      tgPromptTemplateService.listVersions(auth.body.get.id, promptId, page).map {
        case Right(versionsPage) => TgJsonSuccess.justBodyRight(versionsPage.mapItem(toVersionDto))
        case Left(error) =>
          error.toBadRequestLeft[PaginatedResponse[TgPromptTemplateVersionDto]]
      }
    }
  }

  val deleteVersion = deleteVersionEndpoint.serverLogic { auth => version =>
    tgPromptTemplateService.deleteVersion(auth.body.get.id, version).map {
      case Right(_)    => TgJsonSuccess.justMessageRight("Prompt version deleted successfully")
      case Left(error) => error.toBadRequestLeft[TgJsonSuccess.TgEmptyBody]
    }
  }

  val delete = deleteByIdEndpoint.serverLogic { auth => id =>
    tgPromptTemplateService.delete(auth.body.get.id, id).map {
      case Right(_)    => TgJsonSuccess.justMessageRight("Prompt deleted successfully")
      case Left(error) => error.toBadRequestLeft[TgJsonSuccess.TgEmptyBody]
    }
  }
}
