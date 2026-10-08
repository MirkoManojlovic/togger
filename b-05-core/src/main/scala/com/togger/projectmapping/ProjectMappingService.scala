package com.togger.projectmapping

import cats.effect.IO
import com.togger.common.{DbPageRequest, PaginatedResponse}
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

  def findPaginatedByProjectIdDb(
    projectIdDb: UUID,
    page: DbPageRequest
  ): IO[PaginatedResponse[ProjectMapping]] =
    projectMappingRepo.findPaginatedByProjectIdDb(projectIdDb, page).transact(transactor)

  def findPaginatedByProjectIdDump(
    projectIdDump: UUID,
    page: DbPageRequest
  ): IO[PaginatedResponse[ProjectMapping]] =
    projectMappingRepo.findPaginatedByProjectIdDump(projectIdDump, page).transact(transactor)

  def deleteByIds(projectIdDb: UUID, projectIdDump: UUID): IO[Int] =
    projectMappingRepo.deleteByIds(projectIdDb, projectIdDump).transact(transactor)

}
