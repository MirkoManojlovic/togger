package com.togger.projectlabel

import com.togger.auth.SecuredEndpoint
import com.togger.common.TgEndpoints._
import com.togger.common.TgJsonSuccess
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.dto._
import io.circe.generic.auto._
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

import java.util.UUID

class ProjectLabelCategoryEndpoints(securedEndpoint: SecuredEndpoint) {

  private val basePath: EndpointInput[Unit] = "project-label-category"

  val saveEndpoint: TgSecuredEndpoint[CreateProjectLabelCategoryReq, CreateProjectLabelCategoryResp] =
    securedEndpoint.endpoint.post
      .in(basePath)
      .in(jsonBody[CreateProjectLabelCategoryReq])
      .out(jsonBody[TgJsonSuccess[CreateProjectLabelCategoryResp]])

  val findByIdEndpoint: TgSecuredEndpoint[UUID, FindProjectLabelCategoryResp] =
    securedEndpoint.endpoint.get
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[FindProjectLabelCategoryResp]])

  val updateEndpoint: TgSecuredEndpoint[UpdateProjectLabelCategoryResp, FindProjectLabelCategoryResp] =
    securedEndpoint.endpoint.put
      .in(basePath)
      .in(jsonBody[UpdateProjectLabelCategoryResp])
      .out(jsonBody[TgJsonSuccess[FindProjectLabelCategoryResp]])

  val findAllEndpoint: TgSecuredEndpointNoInput[Seq[FindProjectLabelCategoryResp]] =
    securedEndpoint.endpoint.get
      .in(basePath)
      .out(jsonBody[TgJsonSuccess[Seq[FindProjectLabelCategoryResp]]])

  val deleteByIdEndpoint: TgSecuredEndpointEmptyBody[UUID] =
    securedEndpoint.endpoint.delete
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[TgEmptyBody]])

}
