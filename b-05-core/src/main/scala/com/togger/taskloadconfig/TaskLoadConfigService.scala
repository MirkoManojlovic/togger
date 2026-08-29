package com.togger.taskloadconfig

import cats.effect.IO
import com.togger.common.TgClock
import com.togger.logging.TgLogging
import com.togger.util.SplunkMacro.sp
import doobie.Transactor
import doobie.implicits._

import java.util.UUID

class TaskLoadConfigService(
  taskLoadConfigRepo: TaskLoadConfigRepo,
  tgClock: TgClock,
  transactor: Transactor[IO]
) extends TgLogging {

  def create(taskLoadConfig: TaskLoadConfig): IO[TaskLoadConfig] = {
    logger.info(s"Creating ${sp(taskLoadConfig)}")
    taskLoadConfigRepo
      .insert(taskLoadConfig)
      .transact(transactor)
  }

  def findById(id: UUID): IO[TaskLoadConfig] =
    taskLoadConfigRepo.findById(id).transact(transactor)

  def findByProjectId(projectId: UUID): IO[List[TaskLoadConfig]] =
    taskLoadConfigRepo.findByProjectId(projectId).transact(transactor)

  def update(taskLoadConfig: TaskLoadConfig): IO[TaskLoadConfig] = {
    logger.info(s"Updating ${sp(taskLoadConfig)}")
    val now = tgClock.currentInstant
    taskLoadConfigRepo
      .update(taskLoadConfig, now)
      .transact(transactor)
  }

  def deleteById(id: UUID): IO[Int] =
    taskLoadConfigRepo.deleteById(id).transact(transactor)

}
