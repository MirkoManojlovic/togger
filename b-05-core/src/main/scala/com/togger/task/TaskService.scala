package com.togger.task

import cats.effect.IO
import cats.implicits._
import com.togger.logentry.{LogEntryRepo, LogEntryVersionRepo, LogEntryWithVersions}
import com.togger.logging.TgLogging
import com.togger.util.SplunkMacro.sp
import doobie.implicits._
import doobie.{ConnectionIO, Transactor}

import java.util.UUID

class TaskService(
  taskRepo: TaskRepo,
  taskVersionRepo: TaskVersionRepo,
  linkRepo: LinkRepo,
  linkVersionRepo: LinkVersionRepo,
  logEntryRepo: LogEntryRepo,
  logEntryVersionRepo: LogEntryVersionRepo,
  transactor: Transactor[IO]
) extends TgLogging {

  def createTask(
    task: Task,
    firstVersion: TaskVersion,
    newLinks: Seq[LinkWithOneVersion]
  ): IO[(TaskWithOneVersion, List[LinkWithOneVersion])] = {
    logger.info(s"Creating a ${sp(task)}, with ${sp(firstVersion)} and ${sp(newLinks)}")
    (for {
      savedTask <- taskRepo.insert(task)
      savedVersion <- taskVersionRepo.insert(firstVersion.copy(taskId = savedTask.id))
      savedLinks <- insertLinksWithVersions(savedTask.id, newLinks)
    } yield {
      logger.info(s"Task created successfully ${sp(savedTask)} with links ${sp(savedLinks)}")
      TaskWithOneVersion(savedTask, savedVersion) -> savedLinks
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
    taskVersionRepo
      .findByProjectIdWithLatest(projectId)
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

  def findById(id: UUID): IO[(TaskWithVersions, List[LinkWithVersions], List[LogEntryWithVersions])] =
    (for {
      task <- taskRepo.findById(id)
      latestVersion <- taskVersionRepo.findLatestByTaskId(task.id)
      links <- findLinksWithLatestVersion(task.id)
      logEntries <- findLogEntriesWithLatestVersion(task.id)
    } yield (TaskWithVersions(task, Seq(latestVersion)), links, logEntries)).transact(transactor)

  def findByIdWithTaskStatusAndAccount(
    id: UUID
  ): IO[(TaskFull, List[LinkWithVersions], List[LogEntryWithVersions])] =
    (for {
      taskFull <- taskVersionRepo.findByIdWithLatest(id)
      links <- findLinksWithLatestVersion(taskFull.id)
      logEntries <- findLogEntriesWithLatestVersion(taskFull.id)
    } yield (taskFull, links, logEntries)).transact(transactor)

  def updateTask(
    taskId: UUID,
    newVersionContent: TaskVersion,
    linkVersionUpdates: Seq[LinkVersion],
    newLinks: Seq[LinkWithOneVersion]
  ): IO[(TaskWithOneVersion, List[LinkWithOneVersion], List[LinkWithOneVersion])] = {
    logger.info(s"Updating task ${sp(taskId)}, with ${sp(newVersionContent)} and ${sp(linkVersionUpdates)}")
    (for {
      task <- taskRepo.findById(taskId)
      currentVersion <- taskVersionRepo.findLatestByTaskId(taskId)
      savedVersion <-
        if (currentVersion.hasSameContentAs(newVersionContent))
          currentVersion.pure[ConnectionIO]
        else
          taskVersionRepo.insert(newVersionContent.copy(taskId = taskId))
      updatedLinks <- linkVersionUpdates.toList.traverse { version =>
        for {
          currentLinkVersion <- linkVersionRepo.findLatestByLinkId(version.linkId)
          savedVersion <-
            if (currentLinkVersion.hasSameContentAs(version))
              currentLinkVersion.pure[ConnectionIO]
            else
              linkVersionRepo.insert(version)
          link <- linkRepo.findById(savedVersion.linkId)
        } yield LinkWithOneVersion(link, savedVersion)
      }
      savedNewLinks <- insertLinksWithVersions(taskId, newLinks)
    } yield {
      logger.info(s"Task updated successfully ${sp(task)} with links ${sp(updatedLinks)}")
      (TaskWithOneVersion(task, savedVersion), updatedLinks, savedNewLinks)
    }).transact(transactor)
  }
}
