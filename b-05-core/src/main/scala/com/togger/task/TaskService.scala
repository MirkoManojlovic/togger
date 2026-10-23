package com.togger.task

import cats.effect.IO
import cats.implicits._
import com.togger.common.{DbPageRequest, PaginatedResponse}
import com.togger.dto.TaskFilter
import com.togger.logentry.{LogEntryRepo, LogEntryVersionRepo, LogEntryWithVersions}
import com.togger.logging.TgLogging
import com.togger.task.TaskError.{TaskError, TaskNotFound}
import com.togger.util.SplunkMacro.sp
import doobie.implicits._
import doobie.{ConnectionIO, Transactor}

import java.util.UUID

/** Everything one new task is made of, before it is saved. */
case class NewTask(
  task: Task,
  firstVersion: TaskVersion,
  newLinks: Seq[LinkWithOneVersion],
  labelIds: Seq[UUID] = Seq.empty
)

class TaskService(
  taskRepo: TaskRepo,
  taskVersionRepo: TaskVersionRepo,
  linkRepo: LinkRepo,
  linkVersionRepo: LinkVersionRepo,
  logEntryRepo: LogEntryRepo,
  logEntryVersionRepo: LogEntryVersionRepo,
  taskLabelAssignmentRepo: TaskLabelAssignmentRepo,
  transactor: Transactor[IO]
) extends TgLogging {

  def createTask(
    task: Task,
    firstVersion: TaskVersion,
    newLinks: Seq[LinkWithOneVersion],
    labelIds: Seq[UUID] = Seq.empty
  ): IO[(TaskWithOneVersion, List[LinkWithOneVersion])] =
    insertTask(NewTask(task, firstVersion, newLinks, labelIds)).transact(transactor)

  /** Creates the tasks in list order in one transaction: if any of them fails, none is created. */
  def createTasks(newTasks: Seq[NewTask]): IO[List[(TaskWithOneVersion, List[LinkWithOneVersion])]] = {
    logger.info(s"Creating ${newTasks.size} tasks in one transaction")
    newTasks.toList.traverse(insertTask).transact(transactor)
  }

  private def insertTask(newTask: NewTask): ConnectionIO[(TaskWithOneVersion, List[LinkWithOneVersion])] = {
    val NewTask(task, firstVersion, newLinks, labelIds) = newTask
    logger.info(s"Creating a ${sp(task)}, with ${sp(firstVersion)} and ${sp(newLinks)}")
    for {
      savedTask <- taskRepo.insert(task)
      savedVersion <- taskVersionRepo.insert(firstVersion.copy(taskId = savedTask.id))
      savedLinks <- insertLinksWithVersions(savedTask.id, newLinks)
      _ <- labelIds.toList.traverse(labelId =>
        taskLabelAssignmentRepo.insert(TaskLabelAssignment(savedTask.id, labelId, task.createdAt))
      )
    } yield {
      logger.info(s"Task created successfully ${sp(savedTask)} with links ${sp(savedLinks)}")
      TaskWithOneVersion(savedTask, savedVersion) -> savedLinks
    }
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

  def findPaginatedByProjectId(
    projectId: UUID,
    filter: TaskFilter,
    page: DbPageRequest
  ): IO[PaginatedResponse[(TaskFull, List[LinkWithVersions], List[LogEntryWithVersions])]] =
    (for {
      tasksPage <- taskVersionRepo.findPaginatedByProjectId(projectId, filter, page)
      labelsByTaskId <- taskLabelAssignmentRepo.findLabelsByTaskIds(tasksPage.items.map(_.id))
      items <- tasksPage.items.traverse(task =>
        for {
          links <- findLinksWithLatestVersion(task.id)
          logEntries <- findLogEntriesWithLatestVersion(task.id)
        } yield (task.copy(taskLabels = labelsByTaskId.getOrElse(task.id, List.empty)), links, logEntries)
      )
    } yield tasksPage.copy(items = items)).transact(transactor)

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

  def findProjectId(taskId: UUID): IO[UUID] =
    taskRepo.findById(taskId).map(_.projectId).transact(transactor)

  def findProjectIdByVersion(version: UUID): IO[UUID] =
    (for {
      existingVersion <- taskVersionRepo.findByVersion(version)
      task <- taskRepo.findById(existingVersion.taskId)
    } yield task.projectId).transact(transactor)

  def findById(
    id: UUID
  ): IO[(TaskWithVersions, List[LinkWithVersions], List[LogEntryWithVersions], List[TaskLabel])] =
    (for {
      task <- taskRepo.findById(id)
      latestVersion <- taskVersionRepo.findLatestByTaskId(task.id)
      links <- findLinksWithLatestVersion(task.id)
      logEntries <- findLogEntriesWithLatestVersion(task.id)
      taskLabels <- taskLabelAssignmentRepo.findLabelsByTaskId(id)
    } yield (TaskWithVersions(task, Seq(latestVersion)), links, logEntries, taskLabels)).transact(transactor)

  def findByIdWithTaskStatusAndAccount(
    id: UUID
  ): IO[(TaskFull, List[LinkWithVersions], List[LogEntryWithVersions])] =
    (for {
      taskFull <- taskVersionRepo.findByIdWithLatest(id)
      labels <- taskLabelAssignmentRepo.findLabelsByTaskId(taskFull.id)
      links <- findLinksWithLatestVersion(taskFull.id)
      logEntries <- findLogEntriesWithLatestVersion(taskFull.id)
    } yield (taskFull.copy(taskLabels = labels), links, logEntries)).transact(transactor)

  def deleteTask(taskId: UUID): IO[Either[TaskError, Int]] = {
    logger.info(s"Deleting task ${sp(taskId)} with all its versions, links, log entries and label assignments")
    taskRepo
      .deleteById(taskId)
      .map(deleted => Either.cond(deleted > 0, deleted, TaskNotFound: TaskError))
      .transact(transactor)
  }

  def updateTask(
    taskId: UUID,
    newVersionContent: TaskVersion,
    linkVersionUpdates: Seq[LinkVersion],
    newLinks: Seq[LinkWithOneVersion],
    labelIds: Seq[UUID] = Seq.empty,
    archived: Boolean = false
  ): IO[(TaskWithOneVersion, List[LinkWithOneVersion], List[LinkWithOneVersion])] = {
    logger.info(s"Updating task ${sp(taskId)}, with ${sp(newVersionContent)} and ${sp(linkVersionUpdates)}")
    (for {
      existingTask <- taskRepo.findById(taskId)
      task <-
        if (existingTask.archived == archived) existingTask.pure[ConnectionIO]
        else taskRepo.updateArchived(taskId, archived).as(existingTask.copy(archived = archived))
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
      _ <- taskLabelAssignmentRepo.deleteAllByTaskId(taskId)
      _ <- labelIds.toList.traverse(labelId =>
        taskLabelAssignmentRepo.insert(TaskLabelAssignment(taskId, labelId, newVersionContent.updatedAt))
      )
    } yield {
      logger.info(s"Task updated successfully ${sp(task)} with links ${sp(updatedLinks)}")
      (TaskWithOneVersion(task, savedVersion), updatedLinks, savedNewLinks)
    }).transact(transactor)
  }
}
