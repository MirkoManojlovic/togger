package com.togger.dump

import cats.implicits._
import com.togger.account.{Account, AccountRepo}
import com.togger.db.TgPages
import com.togger.dto.MilestoneFilter
import com.togger.logentry.{LogEntryRepo, LogEntryVersionRepo}
import com.togger.milestone._
import com.togger.project.{Project, ProjectAccountRepo, ProjectLabelAssignmentRepo, ProjectRepo}
import com.togger.projectlabel.ProjectLabelCategoryRepo
import com.togger.task._
import com.togger.tasktemplate.{TaskTemplateRepo, TaskTemplateVersionRepo}
import com.togger.timebox.{TimeboxRepo, TimeboxVersionRepo}
import doobie.ConnectionIO

import java.util.UUID

/** Everything of one project that the export tree holds, as read from the database. */
case class ProjectExportSnapshot(
  project: Project,
  taskStatuses: List[TaskStatus],
  taskLabels: List[TaskLabel],
  projectLabels: List[ProjectLabelDumpData],
  milestones: List[MilestoneDumpData],
  tasks: List[TaskDumpData],
  logEntries: List[LogEntryDumpData],
  taskTemplates: List[TaskTemplateDumpData],
  timeboxes: List[TimeboxDumpData],
  memberIds: List[UUID]
)

class DataExportRepo(
  projectRepo: ProjectRepo,
  projectAccountRepo: ProjectAccountRepo,
  projectLabelAssignmentRepo: ProjectLabelAssignmentRepo,
  projectLabelCategoryRepo: ProjectLabelCategoryRepo,
  accountRepo: AccountRepo,
  taskRepo: TaskRepo,
  taskVersionRepo: TaskVersionRepo,
  taskStatusRepo: TaskStatusRepo,
  taskLabelRepo: TaskLabelRepo,
  taskLabelAssignmentRepo: TaskLabelAssignmentRepo,
  linkRepo: LinkRepo,
  linkVersionRepo: LinkVersionRepo,
  logEntryRepo: LogEntryRepo,
  logEntryVersionRepo: LogEntryVersionRepo,
  milestoneRepo: MilestoneRepo,
  milestoneVersionRepo: MilestoneVersionRepo,
  milestoneDateRepo: MilestoneDateRepo,
  milestoneDateVersionRepo: MilestoneDateVersionRepo,
  taskTemplateRepo: TaskTemplateRepo,
  taskTemplateVersionRepo: TaskTemplateVersionRepo,
  timeboxRepo: TimeboxRepo,
  timeboxVersionRepo: TimeboxVersionRepo
) {

  def isAssigned(projectId: UUID, accountId: UUID): ConnectionIO[Boolean] =
    projectAccountRepo.findById(projectId, accountId).attempt.map(_.toOption.isDefined)

  def findAccountById(accountId: UUID): ConnectionIO[Account] =
    accountRepo.findById(accountId)

  /** Reads the whole project, intentionally unbounded. Every list is sorted so that the same data hashes the same. */
  def readSnapshot(projectId: UUID): ConnectionIO[ProjectExportSnapshot] =
    for {
      project <- projectRepo.findById(projectId)
      taskStatuses <- TgPages.readAll(taskStatusRepo.findPaginatedByProjectId(projectId, _))
      taskLabels <- TgPages.readAll(taskLabelRepo.findPaginatedByProjectId(projectId, _))
      projectLabels <- readProjectLabels(projectId)
      milestones <- readMilestones(projectId)
      taskIds <- taskVersionRepo.findAllByProjectIdWithLatest(projectId).map(_.map(_.id))
      tasks <- taskIds.traverse(readTask)
      logEntries <- taskIds.flatTraverse(readLogEntries)
      taskTemplates <- readTaskTemplates(projectId)
      timeboxes <- readTimeboxes(projectId)
      members <- TgPages.readAll(projectAccountRepo.findPaginatedMembers(projectId, _))
    } yield ProjectExportSnapshot(
      project = project,
      taskStatuses = taskStatuses.sortBy(_.id),
      taskLabels = taskLabels.sortBy(_.id),
      projectLabels = projectLabels.sortBy(_.label.id),
      milestones = milestones.sortBy(_.milestone.id),
      tasks = tasks.sortBy(_.task.id),
      logEntries = logEntries.sortBy(_.logEntry.id),
      taskTemplates = taskTemplates.sortBy(_.template.id),
      timeboxes = timeboxes.sortBy(_.timebox.id),
      memberIds = members.map(_.id).sorted
    )

  def findAccounts(accountIds: Set[UUID]): ConnectionIO[List[AccountDumpData]] =
    accountIds.toList.sorted
      .traverse(id => accountRepo.findById(id).attempt.map(_.toOption.map(AccountDumpData.from)))
      .map(_.flatten)

  private def readProjectLabels(projectId: UUID): ConnectionIO[List[ProjectLabelDumpData]] =
    projectLabelAssignmentRepo
      .findProjectLabelsByProjectId(projectId)
      .flatMap(
        _.traverse(label => projectLabelCategoryRepo.findById(label.categoryId).map(ProjectLabelDumpData(label, _)))
      )

  private def readMilestones(projectId: UUID): ConnectionIO[List[MilestoneDumpData]] =
    for {
      milestones <- TgPages.readAll(milestoneRepo.findPaginatedByProjectId(projectId, MilestoneFilter.empty, _))
      dumped <- milestones.traverse { milestone =>
        for {
          versions <- TgPages.readAll(milestoneVersionRepo.findPaginatedByMilestoneId(milestone.id, _))
          dates <- TgPages.readAll(milestoneDateRepo.findPaginatedByMilestoneId(milestone.id, _))
          datesDump <- dates.traverse { date =>
            TgPages
              .readAll(milestoneDateVersionRepo.findPaginatedByMilestoneDateId(date.id, _))
              .map(dateVersions => MilestoneDateDumpData(date, dateVersions.sortBy(_.version)))
          }
        } yield MilestoneDumpData(milestone, versions.sortBy(_.version), datesDump.sortBy(_.milestoneDate.id))
      }
    } yield dumped

  private def readTask(taskId: UUID): ConnectionIO[TaskDumpData] =
    for {
      task <- taskRepo.findById(taskId)
      taskVersions <- taskVersionRepo.findByTaskId(taskId)
      links <- linkRepo.findByTaskId(taskId)
      linksDump <- links.traverse(link =>
        linkVersionRepo.findByLinkId(link.id).map(versions => LinkDumpData(link, versions.sortBy(_.version)))
      )
      labels <- taskLabelAssignmentRepo.findLabelsByTaskId(taskId)
    } yield TaskDumpData(
      task,
      taskVersions.sortBy(_.version),
      linksDump.sortBy(_.link.id),
      labels.map(_.id).sorted
    )

  private def readLogEntries(taskId: UUID): ConnectionIO[List[LogEntryDumpData]] =
    for {
      logEntries <- logEntryRepo.findByTaskId(taskId)
      dumped <- logEntries.traverse(logEntry =>
        logEntryVersionRepo
          .findByLogEntryId(logEntry.id)
          .map(versions => LogEntryDumpData(logEntry, versions.sortBy(_.version)))
      )
    } yield dumped

  private def readTaskTemplates(projectId: UUID): ConnectionIO[List[TaskTemplateDumpData]] =
    for {
      templates <- TgPages.readAll(taskTemplateRepo.findPaginatedFullByProjectId(projectId, _))
      dumped <- templates.traverse { full =>
        TgPages
          .readAll(taskTemplateVersionRepo.findPaginatedByTemplateId(full.template.id, _))
          .map(versions => TaskTemplateDumpData(full.template, versions.sortBy(_.version)))
      }
    } yield dumped

  private def readTimeboxes(projectId: UUID): ConnectionIO[List[TimeboxDumpData]] =
    for {
      timeboxes <- TgPages.readAll(timeboxRepo.findPaginatedByProjectId(projectId, _))
      dumped <- timeboxes.traverse { timebox =>
        for {
          versions <- TgPages.readAll(timeboxVersionRepo.findPaginatedByTimeboxId(timebox.id, _))
          versionsDump <- versions.traverse { version =>
            timeboxVersionRepo
              .findPlannedTasks(version.version)
              .map(tasks => TimeboxVersionDumpData(version, tasks.map(_.version).sorted))
          }
        } yield TimeboxDumpData(timebox, versionsDump.sortBy(_.version.version))
      }
    } yield dumped

}

object DataExportRepo {

  /** The accounts that tasks, log entries, templates and members refer to. */
  def referencedAccountIds(snapshot: ProjectExportSnapshot): Set[UUID] =
    (snapshot.tasks.flatMap { t =>
      t.task.createdBy :: t.taskVersions.flatMap(v => v.assignedTo.toList ++ v.closedBy.toList)
    } ++
      snapshot.logEntries.flatMap(le => le.logEntry.createdBy :: le.versions.map(_.createdBy)) ++
      snapshot.taskTemplates.map(_.template.createdBy) ++
      snapshot.memberIds).toSet

}
