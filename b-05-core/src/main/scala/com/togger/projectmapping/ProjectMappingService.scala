package com.togger.projectmapping

import cats.effect.IO
import com.togger.common.TgClock
import com.togger.logging.TgLogging
import com.togger.util.SplunkMacro.sp
import doobie.Transactor
import doobie.implicits._

import java.util.UUID

class ProjectMappingService(
  projectMappingRepo: ProjectMappingRepo,
  tgClock: TgClock,
  transactor: Transactor[IO]
) extends TgLogging {

  def create(projectMapping: ProjectMapping): IO[ProjectMapping] = {
    logger.info(s"Creating ${sp(projectMapping)}")
    projectMappingRepo
      .insert(projectMapping)
      .transact(transactor)
  }

  def findById(id: UUID): IO[ProjectMapping] =
    projectMappingRepo.findById(id).transact(transactor)

  def findByProjectIdDb(projectIdDb: UUID): IO[List[ProjectMapping]] =
    projectMappingRepo.findByProjectIdDb(projectIdDb).transact(transactor)

  def update(projectMapping: ProjectMapping): IO[ProjectMapping] = {
    logger.info(s"Updating ${sp(projectMapping)}")
    val now = tgClock.currentInstant
    projectMappingRepo
      .update(projectMapping, now)
      .transact(transactor)
  }

  def deleteById(id: UUID): IO[Int] =
    projectMappingRepo.deleteById(id).transact(transactor)

}
