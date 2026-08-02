package com.togger.controllers.task

import com.togger.common.TgError._
import com.togger.common.TgJsonSuccess
import com.togger.controllers.task.LinkDtoMapper.toVersionDto
import com.togger.dto.LinkVersionDto
import com.togger.task.{LinkEndpoints, LinkService}

class LinkController(
  linkEndpoints: LinkEndpoints,
  linkService: LinkService
) {

  val listVersions = linkEndpoints.listVersionsEndpoint.serverLogic { auth => linkId =>
    linkService
      .listVersions(linkId)
      .map(versions => TgJsonSuccess.justBodyRight(versions.map(toVersionDto)))
  }

  val updateVersion = linkEndpoints.updateVersionEndpoint.serverLogic { auth => dto =>
    linkService
      .updateVersionContent(dto.version, dto.name, dto.url)
      .map {
        case Right(version) => TgJsonSuccess.justBodyRight(toVersionDto(version))
        case Left(error)    => error.toBadRequestLeft[LinkVersionDto]
      }
  }

  val deleteVersion = linkEndpoints.deleteVersionEndpoint.serverLogic { auth => version =>
    linkService
      .deleteVersion(version)
      .map {
        case Right(_)    => TgJsonSuccess.justMessageRight("Link version deleted successfully")
        case Left(error) => error.toBadRequestLeft[TgJsonSuccess.TgEmptyBody]
      }
  }
}
