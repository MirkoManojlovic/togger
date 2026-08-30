package com.togger.dump

import cats.implicits._
import com.togger.account.{Account, AccountRepo}
import com.togger.logentry.{LogEntryRepo, LogEntryVersionRepo}
import com.togger.project.{Project, ProjectAccountRepo, ProjectRepo}
import com.togger.task._
import doobie.ConnectionIO

import java.util.UUID

class TaskDumpRepo(
  projectRepo: ProjectRepo,
  projectAccountRepo: ProjectAccountRepo,
  accountRepo: AccountRepo,
  taskRepo: TaskRepo,
  taskVersionRepo: TaskVersionRepo,
  taskStatusRepo: TaskStatusRepo,
  linkRepo: LinkRepo,
  linkVersionRepo: LinkVersionRepo,
  logEntryRepo: LogEntryRepo,
  logEntryVersionRepo: LogEntryVersionRepo
) {

  def isAssigned(projectId: UUID, accountId: UUID): ConnectionIO[Boolean] =
    projectAccountRepo.findById(projectId, accountId).attempt.map(_.toOption.isDefined)

  def findAccountById(accountId: UUID): ConnectionIO[Account] =
    accountRepo.findById(accountId)

  def getProjectAndTaskStatuses(projectId: UUID): ConnectionIO[(Project, List[TaskStatus])] =
    for {
      project <- projectRepo.findById(projectId)
      taskStatuses <- taskStatusRepo.findByProjectId(projectId)
    } yield project -> taskStatuses

  def getAllTasksForProject(projectId: UUID): ConnectionIO[List[TaskDumpData]] =
    for {
      tasks <- taskVersionRepo.findByProjectIdWithLatest(projectId)
      dumpData <- tasks.toList.traverse(task => getTaskDumpData(task.id))
    } yield dumpData

  private def getTaskDumpData(taskId: UUID): ConnectionIO[TaskDumpData] =
    for {
      task <- taskRepo.findById(taskId)
      taskVersions <- taskVersionRepo.findByTaskId(taskId)
      links <- linkRepo.findByTaskId(taskId)
      linksDump <- links.traverse(link =>
        linkVersionRepo.findByLinkId(link.id).map(versions => LinkDumpData(link, versions))
      )
      logEntries <- logEntryRepo.findByTaskId(taskId)
      logEntriesDump <- logEntries.traverse(logEntry =>
        logEntryVersionRepo.findByLogEntryId(logEntry.id).map(versions => LogEntryDumpData(logEntry, versions))
      )
    } yield TaskDumpData(task, taskVersions, linksDump, logEntriesDump)

  private def referencedAccountIds(tasks: List[TaskDumpData]): Set[UUID] =
    tasks.flatMap { t =>
      t.task.createdBy ::
        t.taskVersions.flatMap(v => v.assignedTo.toList ++ v.closedBy.toList) :::
        t.logEntries.flatMap(le => le.logEntry.createdBy :: le.versions.map(_.createdBy))
    }.toSet

  def getReferencedAccounts(tasks: List[TaskDumpData]): ConnectionIO[List[AccountDumpData]] =
    referencedAccountIds(tasks).toList
      .traverse(id => accountRepo.findById(id).attempt.map(_.toOption.map(AccountDumpData.from)))
      .map(_.flatten)

}
