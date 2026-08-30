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

  case class NotFoundPage(url: String) extends PublicPage

  case class CreateTaskPage(projectId: UUID) extends PrivatePage

  case class CreateTaskStatusPage(projectId: UUID) extends PrivatePage

  case class EditTaskStatusPage(projectId: UUID, taskStatusId: UUID) extends PrivatePage

  case class ShowAllTasksPage(projectId: UUID) extends PrivatePage

  case class ShowAllTaskStatusesPage(projectId: UUID) extends PrivatePage

  case class EditTaskPage(projectId: UUID, taskId: UUID) extends PrivatePage

  case object CreateOrganizationPage extends PrivatePage

  case object ShowAllOrganizationsPage extends PrivatePage

  case class EditOrganizationPage(id: UUID) extends PrivatePage

  case class CreateProjectPage(organizationId: UUID) extends PrivatePage

  case class ShowProjectsForOrganizationIdTable(id: UUID) extends PrivatePage

  case class ShowProjectsForOrganizationIdStatus(id: UUID) extends PrivatePage

  case class EditProjectPage(id: UUID) extends PrivatePage

  case class ShowAllTaskConfigsPage(projectId: UUID) extends PrivatePage

  case class CreateTaskDumpConfigPage(projectId: UUID) extends PrivatePage

  case class EditTaskDumpConfigPage(id: UUID) extends PrivatePage

  case class TaskDumpStatusPage(projectId: UUID) extends PrivatePage

  case class CreateTaskLoadConfigPage(projectId: UUID) extends PrivatePage

  case class EditTaskLoadConfigPage(id: UUID) extends PrivatePage

  case class CreateProjectMappingPage(projectId: UUID) extends PrivatePage

  case object HomePage extends PrivatePage
}
