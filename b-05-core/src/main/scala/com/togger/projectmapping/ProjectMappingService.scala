package com.togger.projectmapping

import cats.effect.IO
import com.togger.logging.TgLogging
import com.togger.util.SplunkMacro.sp
import doobie.Transactor
import doobie.implicits._

import java.util.UUID

class ProjectMappingService(
  projectMappingRepo: ProjectMappingRepo,
  transactor: Transactor[IO]
) extends TgLogging {

  def create(projectMapping: ProjectMapping): IO[ProjectMapping] = {
    logger.info(s"Creating ${sp(projectMapping)}")
    projectMappingRepo
      .insert(projectMapping)
      .transact(transactor)
  }

  def findByProjectIdDb(projectIdDb: UUID): IO[List[ProjectMapping]] =
    projectMappingRepo.findByProjectIdDb(projectIdDb).transact(transactor)

  def findByProjectIdDump(projectIdDump: UUID): IO[List[ProjectMapping]] =
    projectMappingRepo.findByProjectIdDump(projectIdDump).transact(transactor)

  def deleteByIds(projectIdDb: UUID, projectIdDump: UUID): IO[Int] =
    projectMappingRepo.deleteByIds(projectIdDb, projectIdDump).transact(transactor)

}
