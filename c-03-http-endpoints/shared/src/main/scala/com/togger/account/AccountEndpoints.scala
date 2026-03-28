package com.togger.account
import com.togger.auth.SecuredEndpoint
import com.togger.common.TgEndpoints.TgSecuredEndpoint
import com.togger.common.TgJsonSuccess
import com.togger.dto.SearchAccountResp
import io.circe.generic.auto._
import sttp.tapir.generic.auto.schemaForCaseClass
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.{EndpointInput, path}

class AccountEndpoints(securedEndpoint: SecuredEndpoint) {

  private val basePath: EndpointInput[Unit] = "account"

  def searchByFirstNameLastNameEmail: TgSecuredEndpoint[String, SearchAccountResp] =
    securedEndpoint.endpoint.get
      .in(basePath / "term" / path[String]("term"))
      .out(jsonBody[TgJsonSuccess[SearchAccountResp]])

}
