package com.togger.milestone

import cats.effect.IO
import cats.implicits._
import com.togger.common.{DbPageRequest, PaginatedResponse}
import com.togger.logging.TgLogging
import com.togger.milestone.MilestoneDateError.{
  CannotDeleteLastVersion,
  CannotEditLatestVersion,
  MilestoneDateError,
  MilestoneDateNotFound,
  VersionNotFound
}
import com.togger.util.SplunkMacro.sp
import doobie.{ConnectionIO, Transactor}
import doobie.implicits._
import doobie.util.invariant.UnexpectedEnd

import java.util.UUID

class MilestoneDateService(
  milestoneDateRepo: MilestoneDateRepo,
  milestoneDateVersionRepo: MilestoneDateVersionRepo,
  transactor: Transactor[IO]
) extends TgLogging {

  def createMilestoneDate(
    milestoneDate: MilestoneDate,
    firstVersion: MilestoneDateVersion
  ): IO[MilestoneDateWithOneVersion] = {
    logger.info(s"Creating a ${sp(milestoneDate)}, with ${sp(firstVersion)}")
    (for {
      savedMilestoneDate <- milestoneDateRepo.insert(milestoneDate)
      savedVersion <- milestoneDateVersionRepo.insert(firstVersion.copy(milestoneDateId = savedMilestoneDate.id))
    } yield {
      logger.info(s"Milestone date created successfully ${sp(savedMilestoneDate)} with ${sp(savedVersion)}")
      MilestoneDateWithOneVersion(savedMilestoneDate, savedVersion)
    }).transact(transactor)
  }

  def saveNewVersion(
    milestoneDateId: UUID,
    newVersion: MilestoneDateVersion
  ): IO[Either[MilestoneDateError, MilestoneDateWithOneVersion]] =
    (for {
      milestoneDate <- milestoneDateRepo.findById(milestoneDateId)
      savedVersion <- milestoneDateVersionRepo.insert(newVersion.copy(milestoneDateId = milestoneDateId))
    } yield MilestoneDateWithOneVersion(milestoneDate, savedVersion))
      .transact(transactor)
      .map(Right(_))
      .recoverWith { case UnexpectedEnd =>
        IO.pure(Left(MilestoneDateNotFound))
      }

  def findPaginatedByProjectId(
    projectId: UUID,
    page: DbPageRequest
  ): IO[PaginatedResponse[MilestoneDateWithVersions]] =
    milestoneDateRepo
      .findPaginatedByProjectId(projectId, page)
      .flatMap(withLatestVersions)
      .transact(transactor)

  def getCurrent(milestoneDateId: UUID): IO[Either[MilestoneDateError, MilestoneDateWithOneVersion]] =
    (for {
      milestoneDate <- milestoneDateRepo.findById(milestoneDateId)
      latestVersion <- milestoneDateVersionRepo.findLatestByMilestoneDateId(milestoneDateId)
    } yield MilestoneDateWithOneVersion(milestoneDate, latestVersion))
      .transact(transactor)
      .map(Right(_))
      .recoverWith { case UnexpectedEnd =>
        IO.pure(Left(MilestoneDateNotFound))
      }

  def findPaginatedByMilestoneId(
    milestoneId: UUID,
    page: DbPageRequest
  ): IO[PaginatedResponse[MilestoneDateWithVersions]] =
    milestoneDateRepo
      .findPaginatedByMilestoneId(milestoneId, page)
      .flatMap(withLatestVersions)
      .transact(transactor)

  private def withLatestVersions(
    datesPage: PaginatedResponse[MilestoneDate]
  ): ConnectionIO[PaginatedResponse[MilestoneDateWithVersions]] =
    datesPage.items
      .traverse(date =>
        milestoneDateVersionRepo
          .findLatestByMilestoneDateId(date.id)
          .map(version => MilestoneDateWithVersions(date, Seq(version)))
      )
      .map(combined => datesPage.copy(items = combined))

  def listVersions(milestoneDateId: UUID, page: DbPageRequest): IO[PaginatedResponse[MilestoneDateVersion]] =
    milestoneDateVersionRepo.findPaginatedByMilestoneDateId(milestoneDateId, page).transact(transactor)

  def updateVersionContent(
    version: UUID,
    name: String,
    description: String,
    date: java.time.Instant
  ): IO[Either[MilestoneDateError, MilestoneDateVersion]] =
    (for {
      existingVersion <- milestoneDateVersionRepo.findByVersion(version)
      latestVersion <- milestoneDateVersionRepo.findLatestByMilestoneDateId(existingVersion.milestoneDateId)
      result <-
        if (existingVersion.version == latestVersion.version)
          (CannotEditLatestVersion: MilestoneDateError).asLeft[MilestoneDateVersion].pure[doobie.ConnectionIO]
        else
          milestoneDateVersionRepo
            .updateContent(existingVersion.withNewContent(name, description, date))
            .map(_.asRight[MilestoneDateError])
    } yield result)
      .transact(transactor)
      .recoverWith { case UnexpectedEnd =>
        IO.pure(Left(VersionNotFound))
      }

  def deleteVersion(version: UUID): IO[Either[MilestoneDateError, Int]] =
    (for {
      existingVersion <- milestoneDateVersionRepo.findByVersion(version)
      latestVersion <- milestoneDateVersionRepo.findLatestByMilestoneDateId(existingVersion.milestoneDateId)
      result <-
        if (existingVersion.version != latestVersion.version)
          milestoneDateVersionRepo.deleteByVersion(version).map(_.asRight[MilestoneDateError])
        else
          milestoneDateVersionRepo
            .countByMilestoneDateId(existingVersion.milestoneDateId)
            .flatMap(count =>
              if (count <= 1)
                (CannotDeleteLastVersion: MilestoneDateError).asLeft[Int].pure[doobie.ConnectionIO]
              else
                milestoneDateVersionRepo.deleteByVersion(version).map(_.asRight[MilestoneDateError])
            )
    } yield result)
      .transact(transactor)
      .recoverWith { case UnexpectedEnd =>
        IO.pure(Left(VersionNotFound))
      }

  def deleteMilestoneDate(id: UUID): IO[Int] =
    milestoneDateRepo.deleteById(id).transact(transactor)

}
