package com.togger.task

import cats.effect.IO
import cats.implicits._
import com.togger.common.TgClock
import com.togger.logentry.{LogEntryRepo, LogEntryVersionRepo, LogEntryWithVersions}
import com.togger.logging.TgLogging
import com.togger.util.SplunkMacro.sp
import doobie.{ConnectionIO, Transactor}
import doobie.implicits._

import java.util.UUID

class TaskService(
  taskRepo: TaskRepo,
  linkRepo: LinkRepo,
  logEntryRepo: LogEntryRepo,
  logEntryVersionRepo: LogEntryVersionRepo,
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

  def findByProjectId(projectId: UUID): IO[List[(TaskFull, List[Link], List[LogEntryWithVersions])]] =
    taskRepo
      .findByProjectId(projectId)
      .flatMap { tasks =>
        tasks
          .map(task =>
            for {
              links <- linkRepo.findByTaskId(task.id)
              logEntries <- findLogEntriesWithLatestVersion(task.id)
            } yield (task, links, logEntries)
          )
          .toList
          .sequence
      }
      .transact(transactor)

  private def findLogEntriesWithLatestVersion(taskId: UUID): ConnectionIO[List[LogEntryWithVersions]] =
    for {
      logEntries <- logEntryRepo.findByTaskId(taskId)
      logEntriesWithVersion <-
        logEntries.traverse(logEntry =>
          logEntryVersionRepo
            .findLatestByLogEntryId(logEntry.id)
            .map(version => LogEntryWithVersions(logEntry, Seq(version)))
        )
    } yield logEntriesWithVersion

  def findById(id: UUID): IO[(Task, List[Link], List[LogEntryWithVersions])] =
    (for {
      task <- taskRepo.findById(id)
      links <- linkRepo.findByTaskId(task.id)
      logEntries <- findLogEntriesWithLatestVersion(task.id)
    } yield (task, links, logEntries)).transact(transactor)

  def findByIdWithTaskStatusAndAccount(
    id: UUID
  ): IO[(TaskFull, List[Link], List[LogEntryWithVersions])] =
    (for {
      taskFull <- taskRepo.findByIdWithStatusAndAccount(id)
      links <- linkRepo.findByTaskId(taskFull.id)
      logEntries <- findLogEntriesWithLatestVersion(taskFull.id)
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
