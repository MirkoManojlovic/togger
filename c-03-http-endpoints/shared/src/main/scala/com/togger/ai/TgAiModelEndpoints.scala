package com.togger.ai

import com.togger.auth.SecuredEndpoint
import com.togger.common.TgEndpoints._
import com.togger.common.TgJsonSuccess
import com.togger.dto.TgAiModelDto
import io.circe.generic.auto._
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

class TgAiModelEndpoints(securedEndpoint: SecuredEndpoint) {

  val listEndpoint: TgSecuredEndpoint[Unit, List[TgAiModelDto]] =
    securedEndpoint.endpoint.get
      .in("tg-ai-model")
      .out(jsonBody[TgJsonSuccess[List[TgAiModelDto]]])

}
