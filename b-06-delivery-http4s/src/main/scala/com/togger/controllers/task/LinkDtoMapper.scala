package com.togger.controllers.task

import com.togger.dto.{FindLinkResp, LinkVersionDto}
import com.togger.task.{Link, LinkVersion}

object LinkDtoMapper {

  def toDto(link: Link, version: LinkVersion): FindLinkResp =
    FindLinkResp(
      id = link.id,
      taskId = link.taskId,
      createdAt = link.createdAt,
      latestVersion = version.version,
      name = version.name,
      url = version.url,
      updatedAt = version.updatedAt
    )

  def toVersionDto(version: LinkVersion): LinkVersionDto =
    LinkVersionDto(
      version = version.version,
      linkId = version.linkId,
      name = version.name,
      url = version.url,
      createdAt = version.createdAt,
      updatedAt = version.updatedAt
    )

}
