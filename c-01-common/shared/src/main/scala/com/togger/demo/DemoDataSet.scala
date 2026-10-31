package com.togger.demo

import com.togger.ai.TgAiEndpoints
import com.togger.dto.dataexportconfig.DataExportConfigResp
import com.togger.dto.dataimportconfig.DataImportConfigResp
import com.togger.dto.projectmapping.ProjectMappingResp
import com.togger.dto.{LinkVersionDto, LogEntryVersionDto, TaskVersionDto, TgPromptTemplateVersionDto}
import com.togger.milestone.MilestoneStatus
import com.togger.task.{DefaultTaskLabel, DefaultTaskStatus}

import java.time.{Instant, LocalDate}
import java.util.UUID

case class DemoAccount(id: UUID, firstName: String, lastName: String, email: String, password: String, admin: Boolean)

case class DemoOrganization(id: UUID, name: String, description: String, icon: String, memberIds: List[UUID])

case class DemoProject(
  id: UUID,
  organizationId: UUID,
  name: String,
  description: String,
  icon: String,
  workDays: Set[Int],
  memberIds: List[UUID],
  projectLabelIds: List[UUID]
)

case class DemoProjectLabelCategory(id: UUID, name: String)

case class DemoProjectLabel(id: UUID, name: String, description: String, categoryId: UUID)

case class DemoTaskStatus(id: UUID, projectId: UUID, status: DefaultTaskStatus)

case class DemoTaskLabel(id: UUID, projectId: UUID, label: DefaultTaskLabel)

case class DemoTaskTemplateVersion(id: UUID, name: String, body: String, createdAt: Instant)

case class DemoTaskTemplate(
  id: UUID,
  projectId: UUID,
  taskLabelId: Option[UUID],
  createdBy: UUID,
  createdAt: Instant,
  versions: List[DemoTaskTemplateVersion]
)

case class DemoMilestoneDate(
  id: UUID,
  createdAt: Instant,
  version: UUID,
  name: String,
  description: String,
  date: Instant,
  updatedAt: Instant
)

case class DemoMilestoneSummary(
  id: UUID,
  createdAt: Instant,
  version: UUID,
  text: String,
  fromDate: LocalDate,
  toDate: LocalDate,
  updatedAt: Instant
)

case class DemoMilestone(
  id: UUID,
  projectId: UUID,
  createdAt: Instant,
  version: UUID,
  name: String,
  description: String,
  status: MilestoneStatus,
  startDate: LocalDate,
  dueDate: LocalDate,
  endDate: Option[LocalDate],
  updatedAt: Instant,
  dates: List[DemoMilestoneDate],
  summaries: List[DemoMilestoneSummary]
)

case class DemoLink(id: UUID, createdAt: Instant, versions: List[LinkVersionDto])

case class DemoLogEntry(id: UUID, createdBy: UUID, createdAt: Instant, versions: List[LogEntryVersionDto])

/** Versions are ordered oldest first, the last one is the current content. */
case class DemoTask(
  id: UUID,
  projectId: UUID,
  createdBy: UUID,
  createdAt: Instant,
  archived: Boolean,
  labelIds: List[UUID],
  versions: List[TaskVersionDto],
  links: List[DemoLink],
  logEntries: List[DemoLogEntry]
)

case class DemoTimeboxVersion(
  version: UUID,
  startDate: LocalDate,
  endDate: LocalDate,
  description: Option[String],
  createdAt: Instant,
  changeComment: String,
  taskVersionIds: List[UUID]
)

case class DemoTimebox(id: UUID, projectId: UUID, createdAt: Instant, versions: List[DemoTimeboxVersion])

case class DemoPromptTemplate(id: UUID, createdBy: UUID, createdAt: Instant, versions: List[TgPromptTemplateVersionDto])

case class DemoAiAction(
  id: UUID,
  createdBy: UUID,
  name: String,
  prompt: String,
  promptTemplateId: Option[UUID],
  model: String,
  endpoints: List[TgAiEndpoints],
  createdAt: Instant,
  updatedAt: Instant
)

case class DemoAiActionDefault(id: UUID, accountId: UUID, projectId: UUID, endpoint: TgAiEndpoints, actionId: UUID)

case class DemoProjectMapping(id: UUID, mapping: ProjectMappingResp)

/** The whole demo data set of the frontend preview and of the backend demo seeder. Rows are listed so that every row
  * only refers to rows listed before it. All dates are relative to `today`/`now`.
  */
case class DemoDataSet(
  accounts: List[DemoAccount],
  organizations: List[DemoOrganization],
  projects: List[DemoProject],
  projectLabelCategories: List[DemoProjectLabelCategory],
  projectLabels: List[DemoProjectLabel],
  taskStatuses: List[DemoTaskStatus],
  taskLabels: List[DemoTaskLabel],
  taskTemplates: List[DemoTaskTemplate],
  milestones: List[DemoMilestone],
  promptTemplates: List[DemoPromptTemplate],
  aiActions: List[DemoAiAction],
  aiActionDefaults: List[DemoAiActionDefault],
  tasks: List[DemoTask],
  timeboxes: List[DemoTimebox],
  exportConfigs: List[DataExportConfigResp],
  importConfigs: List[DataImportConfigResp],
  projectMappings: List[DemoProjectMapping]
)

object DemoDataSet {

  def build(today: LocalDate, now: Instant): DemoDataSet = {
    val projects = DemoOrganizationsData.projects
    val tasks = DemoTasksData.tasks(today, now, projects)
    DemoDataSet(
      accounts = DemoAccountsData.accounts,
      organizations = DemoOrganizationsData.organizations,
      projects = projects,
      projectLabelCategories = DemoProjectLabelsData.categories,
      projectLabels = DemoProjectLabelsData.labels,
      taskStatuses = DemoTaskConfigData.statuses,
      taskLabels = DemoTaskConfigData.labels,
      taskTemplates = DemoTaskConfigData.templates(now),
      milestones = DemoMilestonesData.milestones(today, now, projects),
      promptTemplates = DemoAiData.promptTemplates(now),
      aiActions = DemoAiData.actions(now),
      aiActionDefaults = DemoAiData.defaults,
      tasks = tasks,
      timeboxes = DemoTasksData.timeboxes(today, tasks),
      exportConfigs = DemoDataSyncData.exportConfigs(now),
      importConfigs = DemoDataSyncData.importConfigs(now),
      projectMappings = DemoDataSyncData.mappings(now)
    )
  }
}
