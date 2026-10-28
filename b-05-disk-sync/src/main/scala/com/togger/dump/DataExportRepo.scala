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
import com.togger.tgprompttemplate.{TgPromptTemplateRepo, TgPromptTemplateVersionRepo}
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
  tgPromptTemplates: List[TgPromptTemplateDumpData],
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
  milestoneSummaryRepo: MilestoneSummaryRepo,
  milestoneSummaryVersionRepo: MilestoneSummaryVersionRepo,
  taskTemplateRepo: TaskTemplateRepo,
  taskTemplateVersionRepo: TaskTemplateVersionRepo,
  timeboxRepo: TimeboxRepo,
  timeboxVersionRepo: TimeboxVersionRepo,
  tgPromptTemplateRepo: TgPromptTemplateRepo,
  tgPromptTemplateVersionRepo: TgPromptTemplateVersionRepo
) {

  def isAssigned(projectId: UUID, accountId: UUID): ConnectionIO[Boolean] =
    projectAccountRepo.findById(projectId, accountId).attempt.map(_.toOption.isDefined)

  def findAccountById(accountId: UUID): ConnectionIO[Account] =
    accountRepo.findById(accountId)

  /** Reads the whole project, intentionally unbounded. Every list is sorted so that the same data hashes the same. */
  /** The prompts belong to the account, not the project: the tree holds those of the account that exports. */
  def readSnapshot(projectId: UUID, accountId: UUID): ConnectionIO[ProjectExportSnapshot] =
    for {
      project <- projectRepo.findById(projectId)
      taskStatuses <- TgPages.readAll(taskStatusRepo.findPaginatedByProjectId(projectId, _))
      taskLabels <- TgPages.readAll(taskLabelRepo.findPaginatedByProjectId(projectId, _))
      projectLabels <- readProjectLabels(projectId)
      milestones <- readMilestones(projectId)
      tasks <- readTasks(projectId)
      logEntries <- readLogEntries(projectId)
      taskTemplates <- readTaskTemplates(projectId)
      timeboxes <- readTimeboxes(projectId)
      tgPromptTemplates <- readTgPromptTemplates(accountId)
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
      tgPromptTemplates = tgPromptTemplates.sortBy(_.tgPromptTemplate.id),
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
          summaries <- TgPages.readAll(milestoneSummaryRepo.findPaginatedByMilestoneId(milestone.id, _))
          summariesDump <- summaries.traverse { summary =>
            TgPages
              .readAll(milestoneSummaryVersionRepo.findPaginatedByMilestoneSummaryId(summary.id, _))
              .map(summaryVersions => MilestoneSummaryDumpData(summary, summaryVersions.sortBy(_.version)))
          }
        } yield MilestoneDumpData(
          milestone,
          versions.sortBy(_.version),
          datesDump.sortBy(_.milestoneDate.id),
          summariesDump.sortBy(_.milestoneSummary.id)
        )
      }
    } yield dumped

  /** Reads the tasks with a handful of project-wide queries, not a set of queries per task. */
  private def readTasks(projectId: UUID): ConnectionIO[List[TaskDumpData]] =
    for {
      tasks <- taskRepo.findAllByProjectId(projectId)
      taskVersions <- taskVersionRepo.findAllByProjectId(projectId)
      links <- linkRepo.findAllByProjectId(projectId)
      linkVersions <- linkVersionRepo.findAllByProjectId(projectId)
      labels <- taskLabelAssignmentRepo.findLabelsByProjectId(projectId)
    } yield {
      val versionsByTask = taskVersions.groupBy(_.taskId)
      val linkVersionsByLink = linkVersions.groupBy(_.linkId)
      val linksByTask = links.groupBy(_.taskId)
      tasks.map { task =>
        TaskDumpData(
          task,
          versionsByTask.getOrElse(task.id, Nil).sortBy(_.version),
          linksByTask
            .getOrElse(task.id, Nil)
            .map(link => LinkDumpData(link, linkVersionsByLink.getOrElse(link.id, Nil).sortBy(_.version)))
            .sortBy(_.link.id),
          labels.getOrElse(task.id, Nil).map(_.id).sorted
        )
      }
    }

  private def readLogEntries(projectId: UUID): ConnectionIO[List[LogEntryDumpData]] =
    for {
      logEntries <- logEntryRepo.findAllByProjectId(projectId)
      versions <- logEntryVersionRepo.findAllByProjectId(projectId)
    } yield {
      val versionsByLogEntry = versions.groupBy(_.logEntryId)
      logEntries.map(logEntry =>
        LogEntryDumpData(logEntry, versionsByLogEntry.getOrElse(logEntry.id, Nil).sortBy(_.version))
      )
    }

  private def readTaskTemplates(projectId: UUID): ConnectionIO[List[TaskTemplateDumpData]] =
    for {
      templates <- TgPages.readAll(taskTemplateRepo.findPaginatedFullByProjectId(projectId, _))
      dumped <- templates.traverse { full =>
        TgPages
          .readAll(taskTemplateVersionRepo.findPaginatedByTemplateId(full.template.id, _))
          .map(versions => TaskTemplateDumpData(full.template, versions.sortBy(_.version)))
      }
    } yield dumped

  private def readTgPromptTemplates(accountId: UUID): ConnectionIO[List[TgPromptTemplateDumpData]] =
    for {
      prompts <- TgPages.readAll(tgPromptTemplateRepo.findPaginatedFullByCreatedBy(accountId, _))
      dumped <- prompts.traverse { full =>
        TgPages
          .readAll(tgPromptTemplateVersionRepo.findPaginatedByTgPromptTemplateId(full.tgPromptTemplate.id, _))
          .map(versions => TgPromptTemplateDumpData(full.tgPromptTemplate, versions.sortBy(_.version)))
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
