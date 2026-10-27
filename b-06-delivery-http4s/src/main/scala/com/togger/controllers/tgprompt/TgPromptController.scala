package com.togger.controllers.tgprompt

import com.togger.common.TgError._
import com.togger.common.{PaginatedResponse, TgClock, TgJsonSuccess}
import com.togger.controllers.common.PageRequestValidation
import com.togger.controllers.tgprompt.TgPromptDtoMapper.{toDto, toVersionDto}
import com.togger.dto.{FindTgPromptResp, TgPromptVersionDto}
import com.togger.tgprompt.{TgPrompt, TgPromptEndpoints, TgPromptService, TgPromptVersion}

import java.util.UUID

class TgPromptController(
  tgPromptService: TgPromptService,
  tgPromptEndpoints: TgPromptEndpoints,
  tgClock: TgClock
) {

  import tgPromptEndpoints._

  val save = saveEndpoint.serverLogic { auth => dto =>
    val now = tgClock.currentInstant
    val promptId = UUID.randomUUID()
    val tgPrompt = TgPrompt(promptId, auth.body.get.id, now)
    val firstVersion = TgPromptVersion(UUID.randomUUID(), promptId, dto.title, dto.prompt, now)
    tgPromptService
      .create(tgPrompt, firstVersion)
      .map(full => TgJsonSuccess.justBodyRight(toDto(full)))
  }

  val newVersion = newVersionEndpoint.serverLogic { auth => dto =>
    tgPromptService.saveNewVersion(auth.body.get.id, dto.id, dto.title, dto.prompt, tgClock.currentInstant).map {
      case Right(full) => TgJsonSuccess.justBodyRight(toDto(full))
      case Left(error) => error.toBadRequestLeft[FindTgPromptResp]
    }
  }

  val findAll = findAllEndpoint.serverLogic { auth => pageRequest =>
    PageRequestValidation.withPage(pageRequest) { page =>
      tgPromptService
        .findPaginated(auth.body.get.id, page)
        .map(promptsPage => TgJsonSuccess.justBodyRight(promptsPage.mapItem(toDto)))
    }
  }

  val findById = findByIdEndpoint.serverLogic { auth => id =>
    tgPromptService.findById(auth.body.get.id, id).map {
      case Right(full) => TgJsonSuccess.justBodyRight(toDto(full))
      case Left(error) => error.toBadRequestLeft[FindTgPromptResp]
    }
  }

  val listVersions = listVersionsEndpoint.serverLogic { auth => request =>
    val (promptId, pageRequest) = request
    PageRequestValidation.withPage(pageRequest) { page =>
      tgPromptService.listVersions(auth.body.get.id, promptId, page).map {
        case Right(versionsPage) => TgJsonSuccess.justBodyRight(versionsPage.mapItem(toVersionDto))
        case Left(error) =>
          error.toBadRequestLeft[PaginatedResponse[TgPromptVersionDto]]
      }
    }
  }

  val deleteVersion = deleteVersionEndpoint.serverLogic { auth => version =>
    tgPromptService.deleteVersion(auth.body.get.id, version).map {
      case Right(_)    => TgJsonSuccess.justMessageRight("Prompt version deleted successfully")
      case Left(error) => error.toBadRequestLeft[TgJsonSuccess.TgEmptyBody]
    }
  }

  val delete = deleteByIdEndpoint.serverLogic { auth => id =>
    tgPromptService.delete(auth.body.get.id, id).map {
      case Right(_)    => TgJsonSuccess.justMessageRight("Prompt deleted successfully")
      case Left(error) => error.toBadRequestLeft[TgJsonSuccess.TgEmptyBody]
    }
  }
}
