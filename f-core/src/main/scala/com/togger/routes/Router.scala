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

  case class CreateTaskPage(projectId: Long) extends PrivatePage

  case class CreateTaskStatusPage(projectId: Long) extends PrivatePage

  case class EditTaskStatusPage(projectId: Long, taskStatusId: UUID) extends PrivatePage

  case class ShowAllTasksPage(projectId: Long) extends PrivatePage

  case class ShowAllTaskStatusesPage(projectId: Long) extends PrivatePage

  case class EditTaskPage(projectId: Long, taskId: UUID) extends PrivatePage

  case object CreateOrganizationPage extends PrivatePage

  case object ShowAllOrganizationsPage extends PrivatePage

  case class EditOrganizationPage(id: UUID) extends PrivatePage

  case class CreateProjectPage(organizationId: UUID) extends PrivatePage

  case class ShowProjectsForOrganizationIdTable(id: UUID) extends PrivatePage

  case class ShowProjectsForOrganizationIdStatus(id: UUID) extends PrivatePage

  case class EditProjectPage(id: Long) extends PrivatePage

  case object HomePage extends PrivatePage
}
