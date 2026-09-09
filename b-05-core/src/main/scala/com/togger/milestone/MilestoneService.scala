package com.togger.milestone

import cats.effect.IO
import cats.implicits._
import com.togger.logging.TgLogging
import com.togger.milestone.MilestoneError.{
  CannotDeleteLastVersion,
  CannotEditLatestVersion,
  MilestoneError,
  MilestoneNotFound,
  VersionNotFound
}
import com.togger.util.SplunkMacro.sp
import doobie.Transactor
import doobie.implicits._
import doobie.util.invariant.UnexpectedEnd

import java.util.UUID

class MilestoneService(
  milestoneRepo: MilestoneRepo,
  milestoneVersionRepo: MilestoneVersionRepo,
  transactor: Transactor[IO]
) extends TgLogging {

  def createMilestone(milestone: Milestone, firstVersion: MilestoneVersion): IO[MilestoneWithOneVersion] = {
    logger.info(s"Creating a ${sp(milestone)}, with ${sp(firstVersion)}")
    (for {
      savedMilestone <- milestoneRepo.insert(milestone)
      savedVersion <- milestoneVersionRepo.insert(firstVersion.copy(milestoneId = savedMilestone.id))
    } yield {
      logger.info(s"Milestone created successfully ${sp(savedMilestone)} with ${sp(savedVersion)}")
      MilestoneWithOneVersion(savedMilestone, savedVersion)
    }).transact(transactor)
  }

  def saveNewVersion(
    milestoneId: UUID,
    newVersion: MilestoneVersion
  ): IO[Either[MilestoneError, MilestoneWithOneVersion]] =
    (for {
      milestone <- milestoneRepo.findById(milestoneId)
      savedVersion <- milestoneVersionRepo.insert(newVersion.copy(milestoneId = milestoneId))
    } yield MilestoneWithOneVersion(milestone, savedVersion))
      .transact(transactor)
      .map(Right(_))
      .recoverWith { case UnexpectedEnd =>
        IO.pure(Left(MilestoneNotFound))
      }

  def getCurrent(milestoneId: UUID): IO[Either[MilestoneError, MilestoneWithOneVersion]] =
    (for {
      milestone <- milestoneRepo.findById(milestoneId)
      latestVersion <- milestoneVersionRepo.findLatestByMilestoneId(milestoneId)
    } yield MilestoneWithOneVersion(milestone, latestVersion))
      .transact(transactor)
      .map(Right(_))
      .recoverWith { case UnexpectedEnd =>
        IO.pure(Left(MilestoneNotFound))
      }

  def findByProjectId(projectId: UUID): IO[List[MilestoneWithVersions]] =
    (for {
      milestones <- milestoneRepo.findByProjectId(projectId)
      combined <- milestones.traverse(milestone =>
        milestoneVersionRepo
          .findLatestByMilestoneId(milestone.id)
          .map(version => MilestoneWithVersions(milestone, Seq(version)))
      )
    } yield combined).transact(transactor)

  def listVersions(milestoneId: UUID): IO[List[MilestoneVersion]] =
    milestoneVersionRepo.findByMilestoneId(milestoneId).transact(transactor)

  def updateVersionContent(
    version: UUID,
    name: String,
    description: String
  ): IO[Either[MilestoneError, MilestoneVersion]] =
    (for {
      existingVersion <- milestoneVersionRepo.findByVersion(version)
      latestVersion <- milestoneVersionRepo.findLatestByMilestoneId(existingVersion.milestoneId)
      result <-
        if (existingVersion.version == latestVersion.version)
          (CannotEditLatestVersion: MilestoneError).asLeft[MilestoneVersion].pure[doobie.ConnectionIO]
        else
          milestoneVersionRepo
            .updateContent(existingVersion.withNewContent(name, description))
            .map(_.asRight[MilestoneError])
    } yield result)
      .transact(transactor)
      .recoverWith { case UnexpectedEnd =>
        IO.pure(Left(VersionNotFound))
      }

  def deleteVersion(version: UUID): IO[Either[MilestoneError, Int]] =
    (for {
      existingVersion <- milestoneVersionRepo.findByVersion(version)
      latestVersion <- milestoneVersionRepo.findLatestByMilestoneId(existingVersion.milestoneId)
      result <-
        if (existingVersion.version != latestVersion.version)
          milestoneVersionRepo.deleteByVersion(version).map(_.asRight[MilestoneError])
        else
          milestoneVersionRepo
            .countByMilestoneId(existingVersion.milestoneId)
            .flatMap(count =>
              if (count <= 1)
                (CannotDeleteLastVersion: MilestoneError).asLeft[Int].pure[doobie.ConnectionIO]
              else
                milestoneVersionRepo.deleteByVersion(version).map(_.asRight[MilestoneError])
            )
    } yield result)
      .transact(transactor)
      .recoverWith { case UnexpectedEnd =>
        IO.pure(Left(VersionNotFound))
      }

  def deleteMilestone(id: UUID): IO[Int] =
    milestoneRepo.deleteById(id).transact(transactor)

}
