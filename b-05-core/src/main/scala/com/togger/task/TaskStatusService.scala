package com.togger.task

import cats.effect.IO
import com.togger.common.{DbPageRequest, PaginatedResponse, TgClock}
import com.togger.logging.TgLogging
import com.togger.util.SplunkMacro.sp
import doobie.Transactor
import doobie.implicits._

import java.util.UUID

class TaskStatusService(
  taskStatusRepo: TaskStatusRepo,
  tgClock: TgClock,
  transactor: Transactor[IO]
) extends TgLogging {

  def createTaskStatus(taskStatus: TaskStatus): IO[TaskStatus] = {
    logger.info(s"Creating task status a ${sp(taskStatus)}")
    (for {
      savedTaskStatus <- taskStatusRepo.insert(taskStatus)
    } yield {
      logger.info(s"Task status created successfully ${sp(savedTaskStatus)}")
      savedTaskStatus
    }).transact(transactor)
  }

  def findPaginatedByProjectId(projectId: UUID, page: DbPageRequest): IO[PaginatedResponse[TaskStatus]] =
    taskStatusRepo.findPaginatedByProjectId(projectId, page).transact(transactor)

  def findById(id: UUID) =
    (for {
      taskStatus <- taskStatusRepo.findById(id)
    } yield taskStatus).transact(transactor)

  def updateTask(taskStatus: TaskStatus): IO[TaskStatus] = {
    logger.info(s"Updating a ${sp(taskStatus)}")
    val now = tgClock.currentInstant
    (for {
      updatedTaskStatus <- taskStatusRepo.update(taskStatus, now)
    } yield {
      logger.info(s"Task status updated successfully ${sp(updatedTaskStatus)}")
      updatedTaskStatus
    }).transact(transactor)
  }

  def deleteById(id: UUID) = {
    taskStatusRepo.deleteById(id).transact(transactor)
  }
}
