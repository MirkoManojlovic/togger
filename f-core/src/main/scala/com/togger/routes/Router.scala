package com.togger.routes

import enumeratum._

import java.util.UUID

sealed trait ToggerPermission extends EnumEntry

object ToggerPermission extends Enum[ToggerPermission] {

  override def values: IndexedSeq[ToggerPermission] = findValues

  case object DeveloperUserPermission extends ToggerPermission

  case object ManagerUserPermission extends ToggerPermission
}

sealed trait FrontendPage {
  def permissions: Set[ToggerPermission]

  def needsAuth: Boolean

  //  def module: PageModuleGeneric
}

sealed trait PublicPage extends FrontendPage {
  def permissions: Set[ToggerPermission] = Set.empty

  def needsAuth: Boolean = false
}

sealed trait PrivatePage extends FrontendPage {
  def permissions: Set[ToggerPermission] = Set.empty

  def needsAuth: Boolean = true
}

object Pages {
  case object LoginPage extends PublicPage

  case object ChangePasswordPage extends PrivatePage

  case object ApexChartDemoPage extends PrivatePage

  case class NotFoundPage(url: String) extends PublicPage

  case class CreateTaskPage(projectId: UUID) extends PrivatePage

  case class ProjectHomePage(projectId: UUID) extends PrivatePage
  case class CreateTaskStatusPage(projectId: UUID) extends PrivatePage

  case class EditTaskStatusPage(projectId: UUID, taskStatusId: UUID) extends PrivatePage

  case class CreateTaskLabelPage(projectId: UUID) extends PrivatePage

  case class EditTaskLabelPage(projectId: UUID, taskLabelId: UUID) extends PrivatePage

  case class ShowAllTaskLabelsPage(projectId: UUID, list: ListParams = ListParams.default) extends PrivatePage

  case class CreateTaskTemplatePage(projectId: UUID) extends PrivatePage

  case class EditTaskTemplatePage(projectId: UUID, taskTemplateId: UUID) extends PrivatePage

  case class ShowAllTaskTemplatesPage(projectId: UUID, list: ListParams = ListParams.default) extends PrivatePage

  case class ShowAllTasksPage(projectId: UUID, list: TaskListParams = TaskListParams.default) extends PrivatePage

  case class ShowAllTaskStatusesPage(projectId: UUID, list: ListParams = ListParams.default) extends PrivatePage

  case class EditTaskPage(projectId: UUID, taskId: UUID) extends PrivatePage

  case object CreateOrganizationPage extends PrivatePage

  case class ShowAllOrganizationsPage(list: ListParams = ListParams.default) extends PrivatePage

  case class EditOrganizationPage(id: UUID) extends PrivatePage

  case object SyncCheckSettingsPage extends PrivatePage

  case object CreateProjectLabelPage extends PrivatePage

  case class ShowAllProjectLabelsPage(list: ListParams = ListParams.default) extends PrivatePage

  case class EditProjectLabelPage(id: UUID) extends PrivatePage

  case object CreateProjectLabelCategoryPage extends PrivatePage

  case class ShowAllProjectLabelCategoriesPage(list: ListParams = ListParams.default) extends PrivatePage

  case class EditProjectLabelCategoryPage(id: UUID) extends PrivatePage

  case class CreateProjectPage(organizationId: UUID) extends PrivatePage

  case class ShowProjectsForOrganizationIdTable(id: UUID, list: ListParams = ListParams.default) extends PrivatePage

  case class EditProjectPage(id: UUID) extends PrivatePage

  case object SearchProjectsByLabelPage extends PrivatePage

  case class SyncConfigPage(projectId: UUID, list: TaskConfigListParams = TaskConfigListParams.default)
      extends PrivatePage

  case class CreateDataExportConfigPage(projectId: UUID) extends PrivatePage

  case class EditDataExportConfigPage(projectId: UUID, id: UUID) extends PrivatePage

  case class DataExportStatusPage(projectId: UUID) extends PrivatePage

  case class CreateDataImportConfigPage(projectId: UUID) extends PrivatePage

  case class EditDataImportConfigPage(projectId: UUID, id: UUID) extends PrivatePage

  case class CreateProjectMappingPage(projectId: UUID) extends PrivatePage

  case class CreateMilestonePage(projectId: UUID) extends PrivatePage

  case class EditMilestonePage(projectId: UUID, id: UUID) extends PrivatePage
  case class MilestoneReviewPage(projectId: UUID, milestoneId: UUID) extends PrivatePage

  case class ShowMilestonesPage(projectId: UUID, list: MilestoneListParams = MilestoneListParams.default)
      extends PrivatePage

  case class ShowMilestoneHistoryPage(projectId: UUID, history: MilestoneHistoryParams = MilestoneHistoryParams.default)
      extends PrivatePage

  case class ShowTimeboxesPage(projectId: UUID, list: ListParams = ListParams.default) extends PrivatePage

  case class CreateTimeboxPage(projectId: UUID) extends PrivatePage

  case class EditTimeboxPage(projectId: UUID, timeboxId: UUID) extends PrivatePage

  case class TimeboxHistoryPage(projectId: UUID, timeboxId: UUID) extends PrivatePage

  case class TimeboxReviewPage(projectId: UUID, timeboxId: UUID) extends PrivatePage

  case class ShowContributionsPage(projectId: UUID, params: ContributionsParams = ContributionsParams.default)
      extends PrivatePage

  case object HomePage extends PrivatePage
}
