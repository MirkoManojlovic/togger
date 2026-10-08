package com.togger.taskdumpconfig

import cats.effect.IO
import com.togger.common.{DbPageRequest, PaginatedResponse, TgClock}
import com.togger.logging.TgLogging
import com.togger.util.SplunkMacro.sp
import doobie.Transactor
import doobie.implicits._

import java.util.UUID

class TaskDumpConfigService(
  taskDumpConfigRepo: TaskDumpConfigRepo,
  tgClock: TgClock,
  transactor: Transactor[IO]
) extends TgLogging {

  def create(taskDumpConfig: TaskDumpConfig): IO[TaskDumpConfig] = {
    logger.info(s"Creating ${sp(taskDumpConfig)}")
    taskDumpConfigRepo
      .insert(taskDumpConfig)
      .transact(transactor)
  }

  def findById(id: UUID): IO[TaskDumpConfig] =
    taskDumpConfigRepo.findById(id).transact(transactor)

  def findPaginatedByProjectId(projectId: UUID, page: DbPageRequest): IO[PaginatedResponse[TaskDumpConfig]] =
    taskDumpConfigRepo.findPaginatedByProjectId(projectId, page).transact(transactor)

  def update(taskDumpConfig: TaskDumpConfig): IO[TaskDumpConfig] = {
    logger.info(s"Updating ${sp(taskDumpConfig)}")
    val now = tgClock.currentInstant
    taskDumpConfigRepo
      .update(taskDumpConfig, now)
      .transact(transactor)
  }

  def deleteById(id: UUID): IO[Int] =
    taskDumpConfigRepo.deleteById(id).transact(transactor)

}
