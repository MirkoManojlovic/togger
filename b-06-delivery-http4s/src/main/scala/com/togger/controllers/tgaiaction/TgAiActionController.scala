package com.togger.controllers.tgaiaction

import com.togger.ai.TgAiEndpoints
import com.togger.common.TgError._
import com.togger.common.{TgClock, TgJsonSuccess}
import com.togger.controllers.common.PageRequestValidation
import com.togger.controllers.tgaiaction.TgAiActionDtoMapper.{toDefaultDto, toDto}
import com.togger.dto.{FindTgAiActionDefaultResp, FindTgAiActionResp, TgAiEndpointDto}
import com.togger.tgaiaction.{TgAiAction, TgAiActionDefault, TgAiActionEndpoints, TgAiActionService}

import java.util.UUID

class TgAiActionController(
  tgAiActionService: TgAiActionService,
  tgAiActionEndpoints: TgAiActionEndpoints,
  tgClock: TgClock
) {

  import tgAiActionEndpoints._

  val save = saveEndpoint.serverLogic { auth => dto =>
    val now = tgClock.currentInstant
    val action = TgAiAction(UUID.randomUUID(), auth.body.get.id, dto.name, dto.prompt, dto.tgPromptTemplateId, dto.model, now, now)
    tgAiActionService.create(action, dto.endpointIds).map {
      case Right(saved) => TgJsonSuccess.justBodyRight(toDto(saved))
      case Left(error)  => error.toBadRequestLeft[FindTgAiActionResp]
    }
  }

  val update = updateEndpoint.serverLogic { auth => dto =>
    val action = TgAiAction(
      id = dto.id,
      createdBy = auth.body.get.id,
      name = dto.name,
      prompt = dto.prompt,
      tgPromptTemplateId = dto.tgPromptTemplateId,
      model = dto.model,
      createdAt = tgClock.currentInstant,
      updatedAt = tgClock.currentInstant
    )
    tgAiActionService.update(action, dto.endpointIds).map {
      case Right(saved) => TgJsonSuccess.justBodyRight(toDto(saved))
      case Left(error)  => error.toBadRequestLeft[FindTgAiActionResp]
    }
  }

  val findAll = findAllEndpoint.serverLogic { auth => pageRequest =>
    PageRequestValidation.withPage(pageRequest) { page =>
      tgAiActionService
        .findPaginated(auth.body.get.id, page)
        .map(actionsPage => TgJsonSuccess.justBodyRight(actionsPage.mapItem(toDto)))
    }
  }

  val findById = findByIdEndpoint.serverLogic { auth => id =>
    tgAiActionService.findById(auth.body.get.id, id).map {
      case Right(found) => TgJsonSuccess.justBodyRight(toDto(found))
      case Left(error)  => error.toBadRequestLeft[FindTgAiActionResp]
    }
  }

  val delete = deleteByIdEndpoint.serverLogic { auth => id =>
    tgAiActionService.delete(auth.body.get.id, id).map {
      case Right(_)    => TgJsonSuccess.justMessageRight("AI action deleted successfully")
      case Left(error) => error.toBadRequestLeft[TgJsonSuccess.TgEmptyBody]
    }
  }

  val listEndpoints = listEndpointsEndpoint.serverLogic { _ => _ =>
    cats.effect.IO.pure(
      TgJsonSuccess.justBodyRight(TgAiEndpoints.values.map(e => TgAiEndpointDto(e.value, e.endpointName)).toList)
    )
  }

  val findDefaults = findDefaultsEndpoint.serverLogic { auth => projectId =>
    tgAiActionService
      .findDefaults(auth.body.get.id, projectId)
      .map(defaults => TgJsonSuccess.justBodyRight(defaults.map(toDefaultDto)))
  }

  val setDefault = setDefaultEndpoint.serverLogic { auth => dto =>
    tgAiActionService.setDefault(TgAiActionDefault(auth.body.get.id, dto.projectId, dto.endpointId, dto.actionId)).map {
      case Right(saved) => TgJsonSuccess.justBodyRight(toDefaultDto(saved))
      case Left(error)  => error.toBadRequestLeft[FindTgAiActionDefaultResp]
    }
  }

  val clearDefault = clearDefaultEndpoint.serverLogic { auth => request =>
    val (projectId, endpointId) = request
    tgAiActionService
      .clearDefault(auth.body.get.id, projectId, endpointId)
      .map(_ => TgJsonSuccess.justMessageRight("AI action default cleared successfully"))
  }
}
