package com.togger.task

import cats.effect.IO
import com.togger.common.{DbPageRequest, PaginatedResponse, TgClock}
import com.togger.logging.TgLogging
import com.togger.util.SplunkMacro.sp
import doobie.Transactor
import doobie.implicits._

import java.util.UUID

class TaskLabelService(
  taskLabelRepo: TaskLabelRepo,
  tgClock: TgClock,
  transactor: Transactor[IO]
) extends TgLogging {

  def createTaskLabel(taskLabel: TaskLabel): IO[TaskLabel] = {
    logger.info(s"Creating task label a ${sp(taskLabel)}")
    (for {
      savedTaskLabel <- taskLabelRepo.insert(taskLabel)
    } yield {
      logger.info(s"Task label created successfully ${sp(savedTaskLabel)}")
      savedTaskLabel
    }).transact(transactor)
  }

  def findPaginatedByProjectId(projectId: UUID, page: DbPageRequest): IO[PaginatedResponse[TaskLabel]] =
    taskLabelRepo.findPaginatedByProjectId(projectId, page).transact(transactor)

  def findById(id: UUID) =
    (for {
      taskLabel <- taskLabelRepo.findById(id)
    } yield taskLabel).transact(transactor)

  def updateTask(taskLabel: TaskLabel): IO[TaskLabel] = {
    logger.info(s"Updating a ${sp(taskLabel)}")
    val now = tgClock.currentInstant
    (for {
      updatedTaskLabel <- taskLabelRepo.update(taskLabel, now)
    } yield {
      logger.info(s"Task label updated successfully ${sp(updatedTaskLabel)}")
      updatedTaskLabel
    }).transact(transactor)
  }

  def deleteById(id: UUID) = {
    taskLabelRepo.deleteById(id).transact(transactor)
  }
}
