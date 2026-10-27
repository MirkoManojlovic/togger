package com.togger.milestone

import cats.effect.IO
import cats.implicits._
import com.togger.common.{DbPageRequest, PaginatedResponse}
import com.togger.logging.TgLogging
import com.togger.milestone.MilestoneSummaryError.{
  CannotDeleteLastVersion,
  CannotEditLatestVersion,
  MilestoneSummaryError,
  MilestoneSummaryNotFound,
  VersionNotFound
}
import com.togger.util.SplunkMacro.sp
import doobie.{ConnectionIO, Transactor}
import doobie.implicits._
import doobie.util.invariant.UnexpectedEnd

import java.time.LocalDate
import java.util.UUID

class MilestoneSummaryService(
  milestoneSummaryRepo: MilestoneSummaryRepo,
  milestoneSummaryVersionRepo: MilestoneSummaryVersionRepo,
  transactor: Transactor[IO]
) extends TgLogging {

  def createMilestoneSummary(
    milestoneSummary: MilestoneSummary,
    firstVersion: MilestoneSummaryVersion
  ): IO[MilestoneSummaryWithOneVersion] = {
    logger.info(s"Creating a ${sp(milestoneSummary)}, with ${sp(firstVersion)}")
    (for {
      savedMilestoneSummary <- milestoneSummaryRepo.insert(milestoneSummary)
      savedVersion <- milestoneSummaryVersionRepo.insert(firstVersion.copy(milestoneSummaryId = savedMilestoneSummary.id))
    } yield {
      logger.info(s"Milestone summary created successfully ${sp(savedMilestoneSummary)} with ${sp(savedVersion)}")
      MilestoneSummaryWithOneVersion(savedMilestoneSummary, savedVersion)
    }).transact(transactor)
  }

  def saveNewVersion(
    milestoneSummaryId: UUID,
    newVersion: MilestoneSummaryVersion
  ): IO[Either[MilestoneSummaryError, MilestoneSummaryWithOneVersion]] =
    (for {
      milestoneSummary <- milestoneSummaryRepo.findById(milestoneSummaryId)
      savedVersion <- milestoneSummaryVersionRepo.insert(newVersion.copy(milestoneSummaryId = milestoneSummaryId))
    } yield MilestoneSummaryWithOneVersion(milestoneSummary, savedVersion))
      .transact(transactor)
      .map(Right(_))
      .recoverWith { case UnexpectedEnd =>
        IO.pure(Left(MilestoneSummaryNotFound))
      }

  def findMilestoneId(milestoneSummaryId: UUID): IO[Either[MilestoneSummaryError, UUID]] =
    milestoneSummaryRepo
      .findById(milestoneSummaryId)
      .map(milestoneSummary => (milestoneSummary.milestoneId: UUID).asRight[MilestoneSummaryError])
      .transact(transactor)
      .recoverWith { case UnexpectedEnd =>
        IO.pure(Left(MilestoneSummaryNotFound))
      }

  def findMilestoneIdByVersion(version: UUID): IO[Either[MilestoneSummaryError, UUID]] =
    (for {
      existingVersion <- milestoneSummaryVersionRepo.findByVersion(version)
      milestoneSummary <- milestoneSummaryRepo.findById(existingVersion.milestoneSummaryId)
    } yield (milestoneSummary.milestoneId: UUID).asRight[MilestoneSummaryError])
      .transact(transactor)
      .recoverWith { case UnexpectedEnd =>
        IO.pure(Left(VersionNotFound))
      }

  def findPaginatedByProjectId(
    projectId: UUID,
    page: DbPageRequest
  ): IO[PaginatedResponse[MilestoneSummaryWithVersions]] =
    milestoneSummaryRepo
      .findPaginatedByProjectId(projectId, page)
      .flatMap(withLatestVersions)
      .transact(transactor)

  def getCurrent(milestoneSummaryId: UUID): IO[Either[MilestoneSummaryError, MilestoneSummaryWithOneVersion]] =
    (for {
      milestoneSummary <- milestoneSummaryRepo.findById(milestoneSummaryId)
      latestVersion <- milestoneSummaryVersionRepo.findLatestByMilestoneSummaryId(milestoneSummaryId)
    } yield MilestoneSummaryWithOneVersion(milestoneSummary, latestVersion))
      .transact(transactor)
      .map(Right(_))
      .recoverWith { case UnexpectedEnd =>
        IO.pure(Left(MilestoneSummaryNotFound))
      }

  def findPaginatedByMilestoneId(
    milestoneId: UUID,
    page: DbPageRequest
  ): IO[PaginatedResponse[MilestoneSummaryWithVersions]] =
    milestoneSummaryRepo
      .findPaginatedByMilestoneId(milestoneId, page)
      .flatMap(withLatestVersions)
      .transact(transactor)

  private def withLatestVersions(
    summariesPage: PaginatedResponse[MilestoneSummary]
  ): ConnectionIO[PaginatedResponse[MilestoneSummaryWithVersions]] =
    summariesPage.items
      .traverse(summary =>
        milestoneSummaryVersionRepo
          .findLatestByMilestoneSummaryId(summary.id)
          .map(version => MilestoneSummaryWithVersions(summary, Seq(version)))
      )
      .map(withVersions => summariesPage.copy(items = withVersions))

  def listVersions(milestoneSummaryId: UUID, page: DbPageRequest): IO[PaginatedResponse[MilestoneSummaryVersion]] =
    milestoneSummaryVersionRepo.findPaginatedByMilestoneSummaryId(milestoneSummaryId, page).transact(transactor)

  def updateVersionContent(
    version: UUID,
    summary: String,
    startDate: LocalDate,
    endDate: LocalDate
  ): IO[Either[MilestoneSummaryError, MilestoneSummaryVersion]] =
    (for {
      existingVersion <- milestoneSummaryVersionRepo.findByVersion(version)
      latestVersion <- milestoneSummaryVersionRepo.findLatestByMilestoneSummaryId(existingVersion.milestoneSummaryId)
      result <-
        if (existingVersion.version == latestVersion.version)
          (CannotEditLatestVersion: MilestoneSummaryError).asLeft[MilestoneSummaryVersion].pure[doobie.ConnectionIO]
        else
          milestoneSummaryVersionRepo
            .updateContent(existingVersion.withNewContent(summary, startDate, endDate))
            .map(_.asRight[MilestoneSummaryError])
    } yield result)
      .transact(transactor)
      .recoverWith { case UnexpectedEnd =>
        IO.pure(Left(VersionNotFound))
      }

  def deleteVersion(version: UUID): IO[Either[MilestoneSummaryError, Int]] =
    (for {
      existingVersion <- milestoneSummaryVersionRepo.findByVersion(version)
      latestVersion <- milestoneSummaryVersionRepo.findLatestByMilestoneSummaryId(existingVersion.milestoneSummaryId)
      result <-
        if (existingVersion.version != latestVersion.version)
          milestoneSummaryVersionRepo.deleteByVersion(version).map(_.asRight[MilestoneSummaryError])
        else
          milestoneSummaryVersionRepo
            .countByMilestoneSummaryId(existingVersion.milestoneSummaryId)
            .flatMap(count =>
              if (count <= 1)
                (CannotDeleteLastVersion: MilestoneSummaryError).asLeft[Int].pure[doobie.ConnectionIO]
              else
                milestoneSummaryVersionRepo.deleteByVersion(version).map(_.asRight[MilestoneSummaryError])
            )
    } yield result)
      .transact(transactor)
      .recoverWith { case UnexpectedEnd =>
        IO.pure(Left(VersionNotFound))
      }

  def deleteMilestoneSummary(id: UUID): IO[Int] =
    milestoneSummaryRepo.deleteById(id).transact(transactor)

}
