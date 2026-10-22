package com.togger.dataimportconfig

import cats.effect.IO
import com.togger.common.{DbPageRequest, PaginatedResponse, TgClock}
import com.togger.logging.TgLogging
import com.togger.util.SplunkMacro.sp
import doobie.Transactor
import doobie.implicits._

import java.util.UUID

class DataImportConfigService(
  dataImportConfigRepo: DataImportConfigRepo,
  tgClock: TgClock,
  transactor: Transactor[IO]
) extends TgLogging {

  def create(dataImportConfig: DataImportConfig): IO[DataImportConfig] = {
    logger.info(s"Creating ${sp(dataImportConfig)}")
    dataImportConfigRepo
      .insert(dataImportConfig)
      .transact(transactor)
  }

  def findById(id: UUID): IO[DataImportConfig] =
    dataImportConfigRepo.findById(id).transact(transactor)

  def findPaginatedByProjectId(projectId: UUID, page: DbPageRequest): IO[PaginatedResponse[DataImportConfig]] =
    dataImportConfigRepo.findPaginatedByProjectId(projectId, page).transact(transactor)

  def update(dataImportConfig: DataImportConfig): IO[DataImportConfig] = {
    logger.info(s"Updating ${sp(dataImportConfig)}")
    val now = tgClock.currentInstant
    dataImportConfigRepo
      .update(dataImportConfig, now)
      .transact(transactor)
  }

  def deleteById(id: UUID): IO[Int] =
    dataImportConfigRepo.deleteById(id).transact(transactor)

}
