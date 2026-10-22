package com.togger.dataexportconfig

import cats.effect.IO
import com.togger.common.{DbPageRequest, PaginatedResponse, TgClock}
import com.togger.logging.TgLogging
import com.togger.util.SplunkMacro.sp
import doobie.Transactor
import doobie.implicits._

import java.util.UUID

class DataExportConfigService(
  dataExportConfigRepo: DataExportConfigRepo,
  tgClock: TgClock,
  transactor: Transactor[IO]
) extends TgLogging {

  def create(dataExportConfig: DataExportConfig): IO[DataExportConfig] = {
    logger.info(s"Creating ${sp(dataExportConfig)}")
    dataExportConfigRepo
      .insert(dataExportConfig)
      .transact(transactor)
  }

  def findById(id: UUID): IO[DataExportConfig] =
    dataExportConfigRepo.findById(id).transact(transactor)

  def findPaginatedByProjectId(projectId: UUID, page: DbPageRequest): IO[PaginatedResponse[DataExportConfig]] =
    dataExportConfigRepo.findPaginatedByProjectId(projectId, page).transact(transactor)

  def update(dataExportConfig: DataExportConfig): IO[DataExportConfig] = {
    logger.info(s"Updating ${sp(dataExportConfig)}")
    val now = tgClock.currentInstant
    dataExportConfigRepo
      .update(dataExportConfig, now)
      .transact(transactor)
  }

  def deleteById(id: UUID): IO[Int] =
    dataExportConfigRepo.deleteById(id).transact(transactor)

}
