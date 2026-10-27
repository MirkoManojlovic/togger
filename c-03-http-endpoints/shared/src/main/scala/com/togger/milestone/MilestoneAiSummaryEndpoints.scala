package com.togger.milestone

import com.togger.auth.SecuredEndpoint
import com.togger.common.TgEndpoints.TgSecuredEndpoint
import com.togger.common.TgJsonSuccess
import com.togger.dto.{SummariseMilestoneWithAiReq, SummariseMilestoneWithAiResp}
import io.circe.generic.auto._
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

class MilestoneAiSummaryEndpoints(securedEndpoint: SecuredEndpoint) {

  private val basePath: EndpointInput[Unit] = "milestone-ai-summary"

  val summariseEndpoint: TgSecuredEndpoint[SummariseMilestoneWithAiReq, SummariseMilestoneWithAiResp] =
    securedEndpoint.endpoint.post
      .in(basePath)
      .in(jsonBody[SummariseMilestoneWithAiReq])
      .out(jsonBody[TgJsonSuccess[SummariseMilestoneWithAiResp]])
}
