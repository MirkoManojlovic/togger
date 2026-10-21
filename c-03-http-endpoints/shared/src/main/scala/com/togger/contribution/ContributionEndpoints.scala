package com.togger.contribution

import com.togger.auth.SecuredEndpoint
import com.togger.common.TgEndpoints.TgSecuredEndpoint
import com.togger.common.TgJsonSuccess
import com.togger.dto.{ContributionsDto, ContributionsFilter}
import io.circe.generic.auto._
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

import java.time.LocalDate
import java.util.UUID

class ContributionEndpoints(securedEndpoint: SecuredEndpoint) {

  private val basePath: EndpointInput[Unit] = "contribution"

  private val contributionsFilterQuery: EndpointInput[ContributionsFilter] =
    query[Option[LocalDate]]("from")
      .and(query[Option[LocalDate]]("to"))
      .map { case (from, to) => ContributionsFilter(from, to) }(filter => (filter.from, filter.to))

  val findByProjectIdEndpoint: TgSecuredEndpoint[(UUID, ContributionsFilter), ContributionsDto] =
    securedEndpoint.endpoint.get
      .in(basePath / "project" / path[UUID]("id"))
      .in(contributionsFilterQuery)
      .out(jsonBody[TgJsonSuccess[ContributionsDto]])
}
