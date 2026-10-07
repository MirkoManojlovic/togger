package com.togger.ui.components

import com.togger.routes.FrontendPage
import com.togger.routes.Pages._
import com.togger.ui.organization.TgIcons

import java.util.UUID

/** The breadcrumb trail shown above a page.
  *
  * Names of organizations and projects are not known here (they need a backend call), so project-scoped pages hang
  * off the project's task list instead. Pages without a place in the hierarchy (login, 404) have no trail.
  */
object TgBreadcrumbTrail {

  private val organizations =
    TgBreadcrumb("Organizations", Some(ShowAllOrganizationsPage), icon = Some(TgIcons.homeIcon))

  private def link(label: String, page: FrontendPage) = TgBreadcrumb(label, Some(page))

  private def current(label: String) = TgBreadcrumb(label)

  private def tasks(projectId: UUID) = link("Tasks", ShowAllTasksPage(projectId))

  private def underOrganizations(crumbs: TgBreadcrumb*) = organizations +: crumbs

  private def underTasks(projectId: UUID, crumbs: TgBreadcrumb*) = underOrganizations(tasks(projectId) +: crumbs: _*)

  private def underProjectLabels(label: String) =
    underOrganizations(link("Project labels", ShowAllProjectLabelsPage), current(label))

  private def underCategories(label: String) =
    underOrganizations(link("Project label categories", ShowAllProjectLabelCategoriesPage), current(label))

  private def underStatuses(projectId: UUID, label: String) =
    underTasks(projectId, link("Task statuses", ShowAllTaskStatusesPage(projectId)), current(label))

  private def underTaskLabels(projectId: UUID, label: String) =
    underTasks(projectId, link("Task labels", ShowAllTaskLabelsPage(projectId)), current(label))

  private def underConfigs(projectId: UUID, label: String) =
    underTasks(projectId, link("Task configs", ShowAllTaskConfigsPage(projectId)), current(label))

  private def underMilestones(projectId: UUID, label: String) =
    underTasks(projectId, link("Milestones", ShowMilestonesPage(projectId)), current(label))

  private def underTimeboxes(projectId: UUID, label: String) =
    underTasks(projectId, link("Timeboxes", ShowTimeboxesPage(projectId)), current(label))

  def forPage(page: FrontendPage): Seq[TgBreadcrumb] = page match {
    case HomePage | LoginPage | NotFoundPage(_) => Seq.empty

    case ShowAllOrganizationsPage => Seq(organizations.copy(page = None))
    case CreateOrganizationPage   => underOrganizations(current("Create organization"))
    case EditOrganizationPage(_)  => underOrganizations(current("Edit organization"))
    case ShowProjectsForOrganizationIdTable(_) | ShowProjectsForOrganizationIdStatus(_) =>
      underOrganizations(current("Projects"))
    case CreateProjectPage(organizationId) =>
      val projects = link("Projects", ShowProjectsForOrganizationIdStatus(organizationId))
      underOrganizations(projects, current("Create project"))
    case EditProjectPage(_)        => underOrganizations(current("Edit project"))
    case SearchProjectsByLabelPage => underOrganizations(current("Search projects"))
    case ChangePasswordPage        => underOrganizations(current("Change password"))

    case ShowAllProjectLabelsPage          => underOrganizations(current("Project labels"))
    case CreateProjectLabelPage            => underProjectLabels("Create project label")
    case EditProjectLabelPage(_)           => underProjectLabels("Edit project label")
    case ShowAllProjectLabelCategoriesPage => underOrganizations(current("Project label categories"))
    case CreateProjectLabelCategoryPage    => underCategories("Create category")
    case EditProjectLabelCategoryPage(_)   => underCategories("Edit category")

    case ShowAllTasksPage(_)        => underOrganizations(current("Tasks"))
    case CreateTaskPage(projectId)  => underTasks(projectId, current("Create task"))
    case EditTaskPage(projectId, _) => underTasks(projectId, current("Edit task"))

    case ShowAllTaskStatusesPage(projectId) => underTasks(projectId, current("Task statuses"))
    case CreateTaskStatusPage(projectId)    => underStatuses(projectId, "Create status")
    case EditTaskStatusPage(projectId, _)   => underStatuses(projectId, "Edit status")

    case ShowAllTaskLabelsPage(projectId) => underTasks(projectId, current("Task labels"))
    case CreateTaskLabelPage(projectId)   => underTaskLabels(projectId, "Create label")
    case EditTaskLabelPage(projectId, _)  => underTaskLabels(projectId, "Edit label")

    case ShowAllTaskConfigsPage(projectId)   => underTasks(projectId, current("Task configs"))
    case TaskDumpStatusPage(projectId)       => underTasks(projectId, current("Task dump status"))
    case CreateTaskDumpConfigPage(projectId) => underConfigs(projectId, "Create dump config")
    case CreateTaskLoadConfigPage(projectId) => underConfigs(projectId, "Create load config")
    case CreateProjectMappingPage(projectId) => underConfigs(projectId, "Create project mapping")
    case EditTaskDumpConfigPage(_)           => underOrganizations(current("Edit dump config"))
    case EditTaskLoadConfigPage(_)           => underOrganizations(current("Edit load config"))

    case ShowTimeboxesPage(projectId)        => underTasks(projectId, current("Timeboxes"))
    case CreateTimeboxPage(projectId)        => underTimeboxes(projectId, "Create timebox")
    case EditTimeboxPage(projectId, _)       => underTimeboxes(projectId, "Save new timebox version")
    case TimeboxHistoryPage(projectId, _)    => underTimeboxes(projectId, "Timebox history")
    case ShowMilestonesPage(projectId)       => underTasks(projectId, current("Milestones"))
    case ShowMilestoneHistoryPage(projectId) => underMilestones(projectId, "Milestone history")
    case CreateMilestonePage(projectId)      => underMilestones(projectId, "Create milestone")
    case EditMilestonePage(_)                => underOrganizations(current("Edit milestone"))
  }

}
