package com.togger.controllers.ai

import com.togger.ai.{TgAiModelEndpoints, TgAiService}
import com.togger.common.TgError._
import com.togger.common.TgJsonSuccess
import com.togger.dto.TgAiModelDto

class TgAiModelController(tgAiService: TgAiService, tgAiModelEndpoints: TgAiModelEndpoints) {

  import tgAiModelEndpoints._

  val list = listEndpoint.serverLogic { auth => _ =>
    tgAiService.listModels(auth.body.get.id).map {
      case Right(models) => TgJsonSuccess.justBodyRight(models.map(model => TgAiModelDto(model.id)))
      case Left(error)   => error.toBadRequestLeft[List[TgAiModelDto]]
    }
  }
}
