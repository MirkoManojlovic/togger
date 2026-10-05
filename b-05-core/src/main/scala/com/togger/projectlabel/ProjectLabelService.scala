package com.togger.projectlabel

import cats.effect.IO
import com.togger.common.TgClock
import com.togger.logging.TgLogging
import com.togger.util.SplunkMacro.sp
import doobie.Transactor
import doobie.implicits._

import java.util.UUID

class ProjectLabelService(
  projectLabelRepo: ProjectLabelRepo,
  tgClock: TgClock,
  transactor: Transactor[IO]
) extends TgLogging {

  def createProjectLabel(projectLabel: ProjectLabel): IO[ProjectLabel] = {
    logger.info(s"Creating project label ${sp(projectLabel)}")
    (for {
      savedProjectLabel <- projectLabelRepo.insert(projectLabel)
    } yield {
      logger.info(s"Project label created successfully ${sp(savedProjectLabel)}")
      savedProjectLabel
    }).transact(transactor)
  }

  def findAll: IO[List[ProjectLabel]] =
    projectLabelRepo.findAll.transact(transactor)

  def findById(id: UUID): IO[ProjectLabel] =
    (for {
      projectLabel <- projectLabelRepo.findById(id)
    } yield projectLabel).transact(transactor)

  def updateProjectLabel(projectLabel: ProjectLabel): IO[ProjectLabel] = {
    logger.info(s"Updating ${sp(projectLabel)}")
    val now = tgClock.currentInstant
    (for {
      updatedProjectLabel <- projectLabelRepo.update(projectLabel, now)
    } yield {
      logger.info(s"Project label updated successfully ${sp(updatedProjectLabel)}")
      updatedProjectLabel
    }).transact(transactor)
  }

  def deleteById(id: UUID): IO[Int] =
    projectLabelRepo.deleteById(id).transact(transactor)
}
