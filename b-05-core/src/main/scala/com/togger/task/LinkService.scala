package com.togger.task

import cats.effect.IO
import cats.implicits._
import com.togger.logging.TgLogging
import com.togger.task.LinkError.{CannotDeleteLastVersion, CannotEditLatestVersion, LinkError, VersionNotFound}
import doobie.Transactor
import doobie.implicits._
import doobie.util.invariant.UnexpectedEnd

import java.util.UUID

class LinkService(
  linkVersionRepo: LinkVersionRepo,
  transactor: Transactor[IO]
) extends TgLogging {

  def listVersions(linkId: UUID): IO[List[LinkVersion]] =
    linkVersionRepo.findByLinkId(linkId).transact(transactor)

  def updateVersionContent(
    version: UUID,
    name: String,
    url: String
  ): IO[Either[LinkError, LinkVersion]] =
    (for {
      existingVersion <- linkVersionRepo.findByVersion(version)
      latestVersion <- linkVersionRepo.findLatestByLinkId(existingVersion.linkId)
      result <-
        if (existingVersion.version == latestVersion.version)
          (CannotEditLatestVersion: LinkError).asLeft[LinkVersion].pure[doobie.ConnectionIO]
        else
          linkVersionRepo
            .updateContent(existingVersion.withNewContent(name, url))
            .map(_.asRight[LinkError])
    } yield result)
      .transact(transactor)
      .recoverWith { case UnexpectedEnd =>
        IO.pure(Left(VersionNotFound))
      }

  def deleteVersion(version: UUID): IO[Either[LinkError, Int]] =
    (for {
      existingVersion <- linkVersionRepo.findByVersion(version)
      latestVersion <- linkVersionRepo.findLatestByLinkId(existingVersion.linkId)
      result <-
        if (existingVersion.version != latestVersion.version)
          linkVersionRepo.deleteByVersion(version).map(_.asRight[LinkError])
        else
          linkVersionRepo
            .countByLinkId(existingVersion.linkId)
            .flatMap(count =>
              if (count <= 1)
                (CannotDeleteLastVersion: LinkError).asLeft[Int].pure[doobie.ConnectionIO]
              else
                linkVersionRepo.deleteByVersion(version).map(_.asRight[LinkError])
            )
    } yield result)
      .transact(transactor)
      .recoverWith { case UnexpectedEnd =>
        IO.pure(Left(VersionNotFound))
      }

}
