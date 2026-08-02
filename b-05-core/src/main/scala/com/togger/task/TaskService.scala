package com.togger.task

import cats.effect.IO
import cats.implicits._
import com.togger.common.TgClock
import com.togger.logentry.{LogEntryRepo, LogEntryVersionRepo, LogEntryWithVersions}
import com.togger.logging.TgLogging
import com.togger.util.SplunkMacro.sp
import doobie.implicits._
import doobie.{ConnectionIO, Transactor}

import java.util.UUID

class TaskService(
  taskRepo: TaskRepo,
  linkRepo: LinkRepo,
  linkVersionRepo: LinkVersionRepo,
  logEntryRepo: LogEntryRepo,
  logEntryVersionRepo: LogEntryVersionRepo,
  tgClock: TgClock,
  transactor: Transactor[IO]
) extends TgLogging {

  def createTask(task: Task, newLinks: Seq[LinkWithOneVersion]): IO[(Task, List[LinkWithOneVersion])] = {
    logger.info(s"Creating a ${sp(task)}, with ${sp(newLinks)}")
    (for {
      savedTask <- taskRepo.insert(task)
      savedLinks <- insertLinksWithVersions(savedTask.id, newLinks)
    } yield {
      logger.info(s"Task created successfully ${sp(savedTask)} with links ${sp(savedLinks)}")
      savedTask -> savedLinks
    }).transact(transactor)
  }

  private def insertLinksWithVersions(
    taskId: UUID,
    newLinks: Seq[LinkWithOneVersion]
  ): ConnectionIO[List[LinkWithOneVersion]] =
    newLinks.toList.traverse { case LinkWithOneVersion(link, version) =>
      for {
        savedLink <- linkRepo.insert(link.copy(taskId = taskId))
        savedVersion <- linkVersionRepo.insert(version.copy(linkId = savedLink.id))
      } yield LinkWithOneVersion(savedLink, savedVersion)
    }

  def findByProjectId(projectId: UUID): IO[List[(TaskFull, List[LinkWithVersions], List[LogEntryWithVersions])]] =
    taskRepo
      .findByProjectId(projectId)
      .flatMap { tasks =>
        tasks
          .map(task =>
            for {
              links <- findLinksWithLatestVersion(task.id)
              logEntries <- findLogEntriesWithLatestVersion(task.id)
            } yield (task, links, logEntries)
          )
          .toList
          .sequence
      }
      .transact(transactor)

  private def findLinksWithLatestVersion(taskId: UUID): ConnectionIO[List[LinkWithVersions]] =
    for {
      links <- linkRepo.findByTaskId(taskId)
      linksWithVersion <-
        links.traverse(link =>
          linkVersionRepo
            .findLatestByLinkId(link.id)
            .map(version => LinkWithVersions(link, Seq(version)))
        )
    } yield linksWithVersion

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

  def findById(id: UUID): IO[(Task, List[LinkWithVersions], List[LogEntryWithVersions])] =
    (for {
      task <- taskRepo.findById(id)
      links <- findLinksWithLatestVersion(task.id)
      logEntries <- findLogEntriesWithLatestVersion(task.id)
    } yield (task, links, logEntries)).transact(transactor)

  def findByIdWithTaskStatusAndAccount(
    id: UUID
  ): IO[(TaskFull, List[LinkWithVersions], List[LogEntryWithVersions])] =
    (for {
      taskFull <- taskRepo.findByIdWithStatusAndAccount(id)
      links <- findLinksWithLatestVersion(taskFull.id)
      logEntries <- findLogEntriesWithLatestVersion(taskFull.id)
    } yield (taskFull, links, logEntries)).transact(transactor)

  def updateTask(
    task: Task,
    linkVersionUpdates: Seq[LinkVersion],
    newLinks: Seq[LinkWithOneVersion]
  ): IO[(Task, List[LinkWithOneVersion], List[LinkWithOneVersion])] = {
    logger.info(s"Updating a ${sp(task)}, with ${sp(linkVersionUpdates)}")
    (for {
      savedTask <- taskRepo.update(task, tgClock.currentInstant)
      updatedLinks <- linkVersionUpdates.toList.traverse { version =>
        for {
          savedVersion <- linkVersionRepo.insert(version)
          link <- linkRepo.findById(savedVersion.linkId)
        } yield LinkWithOneVersion(link, savedVersion)
      }
      savedNewLinks <- insertLinksWithVersions(savedTask.id, newLinks)
    } yield {
      logger.info(s"Task updated successfully ${sp(savedTask)} with links ${sp(updatedLinks)}")
      (savedTask, updatedLinks, savedNewLinks)
    }).transact(transactor)
  }
}
