package com.togger.controllers.contribution

import cats.effect.IO
import com.togger.common.{Forbidden, TgJsonSuccess}
import com.togger.contribution.{ContributionEndpoints, ContributionService}

class ContributionController(
  contributionEndpoints: ContributionEndpoints,
  contributionService: ContributionService
) {

  // The contributions name every member and what they did, so only the members of the project may read them.
  val findByProjectId = contributionEndpoints.findByProjectIdEndpoint.serverLogic { auth => request =>
    val (projectId, filter) = request
    contributionService.isMember(projectId, auth.body.get.id).flatMap {
      case false => IO.pure(Left(Forbidden))
      case true  => contributionService.findByProjectId(projectId, filter).map(TgJsonSuccess.justBodyRight(_))
    }
  }
}
