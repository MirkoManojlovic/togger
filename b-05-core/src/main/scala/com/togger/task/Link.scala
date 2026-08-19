package com.togger.task

import java.time.Instant
import java.util.UUID

case class Link(
  id: UUID,
  taskId: UUID,
  createdAt: Instant
)

case class LinkVersion(
  version: UUID,
  linkId: UUID,
  name: String,
  url: String,
  createdAt: Instant,
  updatedAt: Instant
) {

  def withNewContent(name: String, url: String): LinkVersion =
    this.copy(name = name, url = url)

  def hasSameContentAs(other: LinkVersion): Boolean =
    name == other.name && url == other.url

}

case class LinkWithOneVersion(link: Link, version: LinkVersion)
case class LinkWithVersions(link: Link, versions: Seq[LinkVersion]) {
  lazy val latestVersion: LinkVersion = versions.head
}
