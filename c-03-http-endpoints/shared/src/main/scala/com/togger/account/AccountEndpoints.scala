package com.togger.account
import com.togger.auth.SecuredEndpoint
import com.togger.common.TgEndpoints.TgSecuredEndpoint
import com.togger.common.PaginationInputs.pageQuery
import com.togger.common.{PageRequest, PaginatedResponse, TgJsonSuccess}
import com.togger.dto.SearchAccountDto
import io.circe.generic.auto._
import sttp.tapir.generic.auto.schemaForCaseClass
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.{EndpointInput, path}

class AccountEndpoints(securedEndpoint: SecuredEndpoint) {

  private val basePath: EndpointInput[Unit] = "account"

  def searchByFirstNameLastNameEmail: TgSecuredEndpoint[(String, PageRequest), PaginatedResponse[SearchAccountDto]] =
    securedEndpoint.endpoint.get
      .in(basePath / "term" / path[String]("term"))
      .in(pageQuery)
      .out(jsonBody[TgJsonSuccess[PaginatedResponse[SearchAccountDto]]])

}
