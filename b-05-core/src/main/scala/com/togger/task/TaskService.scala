package com.togger.task

import cats.effect.IO
import cats.implicits.toTraverseOps
import com.togger.common.TgClock
import com.togger.logentry.{LogEntry, LogEntryRepo}
import com.togger.logging.TgLogging
import com.togger.util.SplunkMacro.sp
import doobie.Transactor
import doobie.implicits._

import java.util.UUID

class TaskService(
  taskRepo: TaskRepo,
  linkRepo: LinkRepo,
  logEntryRepo: LogEntryRepo,
  tgClock: TgClock,
  transactor: Transactor[IO]
) extends TgLogging {

  def createTask(task: Task, links: Seq[Link]): IO[(Task, List[Link])] = {
    logger.info(s"Creating a ${sp(task)}, with ${sp(links)}")
    (for {
      savedTask <- taskRepo.insert(task)
      linksWithTaskId = links.map(link => link.copy(taskId = savedTask.id))
      savedLinks <- linkRepo.insertMany(linksWithTaskId)
    } yield {
      logger.info(s"Task created successfully ${sp(savedTask)} with links ${sp(savedLinks)}")
      savedTask -> savedLinks
    }).transact(transactor)
  }

  def findByProjectId(projectId: UUID): IO[List[(TaskFull, List[Link], List[LogEntry])]] =
    taskRepo
      .findByProjectId(projectId)
      .flatMap { tasks =>
        tasks
          .map(task =>
            for {
              links <- linkRepo.findByTaskId(task.id)
              logEntries <- logEntryRepo.findByTaskId(task.id)
            } yield (task, links, logEntries)
          )
          .toList
          .sequence
      }
      .transact(transactor)

  def findById(id: UUID) =
    (for {
      task <- taskRepo.findById(id)
      links <- linkRepo.findByTaskId(task.id)
      logEntries <- logEntryRepo.findByTaskId(task.id)
    } yield (task, links, logEntries)).transact(transactor)

  def findByIdWithTaskStatusAndAccount(
    id: UUID
  ): IO[(TaskFull, List[Link], List[LogEntry])] =
    (for {
      taskFull <- taskRepo.findByIdWithStatusAndAccount(id)
      links <- linkRepo.findByTaskId(taskFull.id)
      logEntries <- logEntryRepo.findByTaskId(taskFull.id)
    } yield (taskFull, links, logEntries)).transact(transactor)

  def updateTask(task: Task, linksToUpdate: Seq[Link], linksToCreate: Seq[Link]): IO[(Task, Seq[Link], List[Link])] = {
    logger.info(s"Updating a ${sp(task)}, with ${sp(linksToUpdate)}")
    val now = tgClock.currentInstant
    (for {
      savedTask <- taskRepo.update(task, now)
      linksWithTaskId = linksToCreate.map(link => link.copy(taskId = savedTask.id))
      savedLinks <- linkRepo.insertMany(linksWithTaskId)
      updatedLinks <- linksToUpdate.map(link => linkRepo.update(link, now)).sequence
    } yield {
      logger.info(s"Task updated successfully ${sp(savedTask)} with links ${sp(updatedLinks)}")
      (savedTask, updatedLinks, savedLinks)
    }).transact(transactor)
  }
}
