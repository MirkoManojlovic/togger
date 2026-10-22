package com.togger.ui.navbar
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.implicits._
import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveHtmlElement
import com.togger.dto.DataImportResp
import com.togger.dto.organization.OrganizationProjectResp
import com.togger.routes.Pages._
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.AccountService
import com.togger.ui.organization.{OrganizationProjectsLoader, OrganizationWithProjects, TgIcons}
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.DataExportFeService
import com.togger.ui.dataimportconfig.DataImportConfigFeService
import com.togger.ui.toast.Toast
import org.scalajs.dom.HTMLLIElement

import java.util.UUID

case class SidebarLink(
  linkName: String,
  link: Binder[HtmlElement],
  collapsed: Signal[Boolean],
  onExportClick: Option[() => Unit] = None,
  onConfigClick: Option[() => Unit] = None,
  onImportClick: Option[() => Unit] = None,
  icon: String = ""
) {

  private def initials: String = linkName.trim.headOption.map(_.toUpper.toString).getOrElse("")

  private def glyph: String = if (icon.nonEmpty) icon else initials

  private def linkElementProject =
    div(
      cls("flex items-center justify-between"),
      a(
        cls("inline-flex items-center"),
        cls <-- collapsed.map(isCollapsed => if (isCollapsed) Seq("justify-center", "w-full") else Seq.empty),
        title(linkName),
        span(
          cls <-- collapsed.map(isCollapsed =>
            if (isCollapsed) Sidebar.collapsedGlyphBoxStyle else Seq("me-1.5", "-ms-0.5")
          ),
          child.text <-- collapsed.map(isCollapsed => if (isCollapsed) glyph else icon)
        ),
        span(
          cls <-- collapsed.map(isCollapsed => if (isCollapsed) Seq("hidden") else Seq.empty),
          linkName
        ),
        link
      ),
      div(
        cls <-- collapsed.map(isCollapsed => if (isCollapsed) Seq("hidden") else Seq.empty),
        onExportClick.map(exportAction =>
          button(
            `type`("button"),
            TgIcons.downloadIcon("w-3.5 h-3.5"),
            cls(
              "inline-flex items-center justify-center w-5 h-5 text-body bg-transparent border border-default hover:bg-neutral-tertiary hover:text-fg-brand focus:ring-2 focus:ring-neutral-tertiary rounded-base focus:outline-none cursor-pointer"
            ),
            onClick.mapToUnit --> (_ => exportAction())
          )
        ),
        onImportClick.map(importAction =>
          button(
            `type`("button"),
            TgIcons.uploadIcon("w-3.5 h-3.5"),
            cls(
              "inline-flex items-center justify-center w-5 h-5 text-body bg-transparent border border-default hover:bg-neutral-tertiary hover:text-fg-brand focus:ring-2 focus:ring-neutral-tertiary rounded-base focus:outline-none cursor-pointer ml-1"
            ),
            onClick.mapToUnit --> (_ => importAction())
          )
        ),
        onConfigClick.map(configAction =>
          button(
            `type`("button"),
            TgIcons.editIcon("w-3.5 h-3.5"),
            cls(
              "inline-flex items-center justify-center w-5 h-5 text-body bg-transparent border border-default hover:bg-neutral-tertiary hover:text-fg-brand focus:ring-2 focus:ring-neutral-tertiary rounded-base focus:outline-none cursor-pointer ml-1"
            ),
            onClick.mapToUnit --> (_ => configAction())
          )
        )
      )
    )

  private def linkElementOrg =
    div(
      cls("flex items-center"),
      a(
        cls("inline-flex items-center"),
        cls <-- collapsed.map(isCollapsed => if (isCollapsed) Seq("justify-center", "w-full") else Seq.empty),
        title(linkName),
        span(
          cls <-- collapsed.map(isCollapsed =>
            if (isCollapsed) Sidebar.collapsedGlyphBoxStyle else Seq("me-1.5", "-ms-0.5")
          ),
          child.text <-- collapsed.map(isCollapsed => if (isCollapsed) glyph else icon)
        ),
        span(
          cls <-- collapsed.map(isCollapsed => if (isCollapsed) Seq("hidden") else Seq.empty),
          linkName
        ),
        link
      )
    )

  def deselectedProject: ReactiveHtmlElement[HTMLLIElement] =
    li(
      cls("pl-2"),
      linkElementProject.amend(cls(Sidebar.sideBarLinkStyle))
    )

  def deselectedOrg: ReactiveHtmlElement[HTMLLIElement] =
    li(
      linkElementOrg.amend(cls(Sidebar.sideBarLinkStyle))
    )

  def selectedProject: ReactiveHtmlElement[HTMLLIElement] =
    li(
      cls("pl-2"),
      linkElementProject.amend(
        cls(Sidebar.sidebarSelectedLink),
        cls <-- collapsed.map(isCollapsed => if (isCollapsed) Seq("border", "border-fg-brand") else Seq.empty)
      )
    )

  def selectedOrg: ReactiveHtmlElement[HTMLLIElement] =
    li(
      linkElementOrg.amend(
        cls(Sidebar.sidebarSelectedLink),
        cls <-- collapsed.map(isCollapsed => if (isCollapsed) Seq("border", "border-fg-brand") else Seq.empty)
      )
    )

}

class Sidebar(
  router: TgRouter[FrontendPage],
  organizationProjectsLoader: OrganizationProjectsLoader,
  accountService: AccountService,
  dataExportService: DataExportFeService,
  dataImportConfigService: DataImportConfigFeService,
  spinnerService: SpinnerService,
  sidebarCollapseStore: SidebarCollapseStore,
  sidebarRefresh: SidebarRefresh,
  onQuickCreate: () => Unit,
  onBulkCreate: () => Unit,
  onTeamDaily: () => Unit,
  onSlacker: () => Unit,
  val toast: Toast
)(implicit IORuntime: IORuntime)
    extends ToastMessages {

  private val collapsed: Signal[Boolean] = sidebarCollapseStore.current

  private def dumpProject(projectId: UUID): Unit =
    spinnerService
      .runWithSpinner(dataExportService.exportData(projectId))
      .toastMessageDto(dto =>
        s"Data exported to ${dto.results.count(_.success)}/${dto.results.size} configured path(s)"
      )(_ => IO.unit)
      .unsafeRunAndForget()

  private def importAllForProject(projectId: UUID): Unit = {
    val program: IO[Unit] = dataImportConfigService.findAllByProjectId(projectId).flatMap {
      _.fold(
        error => IO(toast.error(error)),
        success => {
          val paths = success.body.get.map(_.importPath).distinct.toList
          if (paths.isEmpty) IO(toast.error("No import paths configured for this project"))
          else
            paths
              .traverse(path => dataExportService.importData(projectId, path).map(_.map(_.body.get)))
              .map { (outcomes: List[Either[com.togger.common.TgJsonFailure, DataImportResp]]) =>
                val successes = outcomes.collect { case Right(dto) => dto }
                outcomes.collect { case Left(failure) => failure }.foreach(toast.error)
                val totalEntities = successes.map(_.results.size).sum
                val failedCount = outcomes.count(_.isLeft)
                if (failedCount == 0) toast.success(s"Imported from ${successes.size} path(s), $totalEntities entity(ies) total")
                else toast.error(s"Imported from ${successes.size}/${outcomes.size} path(s); $failedCount failed")
              }
        }
      )
    }
    spinnerService.runWithSpinner(program).unsafeRunAndForget()
  }

  private def organizationIdForPage(page: FrontendPage): Option[UUID] = page match {
    case CreateProjectPage(organizationId)         => Some(organizationId)
    case ShowProjectsForOrganizationIdTable(id, _) => Some(id)
    case ShowProjectsForOrganizationIdStatus(id)   => Some(id)
    case EditOrganizationPage(id)                  => Some(id)
    case _                                         => None
  }

  private def isProjectSelected(project: OrganizationProjectResp, page: FrontendPage): Boolean =
    Sidebar.projectIdForPage(page).contains(project.id)

  private def isOrganizationSelected(organization: OrganizationWithProjects, page: FrontendPage): Boolean =
    organizationIdForPage(page).contains(organization.id) ||
      Sidebar.projectIdForPage(page).exists(projectId => organization.projects.exists(_.id == projectId))

  private val organizationState: Var[Seq[OrganizationWithProjects]] = Var(Seq.empty)

  // The sidebar lists every organization with all of its projects, read page by page.
  private def reloadOrganizations(): Unit =
    organizationProjectsLoader
      .loadForAccount(accountService.get.get.id)
      .map(
        _.fold(
          error => println(error),
          success => organizationState.set(success.body.get)
        )
      )
      .unsafeRunAndForget()

  reloadOrganizations()

  val element = {
    div(
      sidebarRefresh.requests --> (_ => reloadOrganizations()),
      asideTag(
        idAttr("logo-sidebar"),
        cls(
          "fixed top-14 left-0 z-40 h-[calc(100%-3.5rem)] transition-[width] duration-200 -translate-x-full sm:translate-x-0"
        ),
        cls <-- collapsed.map(isCollapsed => if (isCollapsed) "w-16" else "w-64"),
//        aria-label="Sidebar"

        div(
          cls("h-full px-3 py-4 pb-24 overflow-y-auto bg-neutral-primary-soft border-e border-default"),
          div(
            cls("flex items-center mb-5"),
            cls <-- collapsed.map(isCollapsed => if (isCollapsed) Seq("justify-center") else Seq("justify-end")),
            button(
              `type`("button"),
              title <-- collapsed.map(isCollapsed => if (isCollapsed) "Expand sidebar" else "Collapse sidebar"),
              cls(
                "inline-flex items-center justify-center w-6 h-6 text-body bg-transparent hover:bg-neutral-tertiary hover:text-fg-brand rounded-base focus:outline-none cursor-pointer"
              ),
              child <-- collapsed.map(isCollapsed =>
                if (isCollapsed) TgIcons.chevronRightIcon("w-4 h-4") else TgIcons.chevronLeftIcon("w-4 h-4")
              ),
              onClick.mapToUnit --> (_ => sidebarCollapseStore.toggle())
            )
          ),
          ul(
            cls("space-y-2 font-medium"),
            children <-- organizationState.signal.combineWith(router.currentPageSignal).map {
              case (organizations, page) =>
                organizations
                  .sortBy(_.name)
                  .flatMap {
                    organization =>
                      val orgLink = SidebarLink(
                        linkName = organization.name,
                        link = router.navigateTo(ShowProjectsForOrganizationIdStatus(organization.id)),
                        collapsed = collapsed,
                        icon = organization.icon
                      )
                      val orgRow =
                        if (isOrganizationSelected(organization, page)) orgLink.selectedOrg else orgLink.deselectedOrg
                      orgRow.amend(cls("border-b border-default-medium")) +:
                        organization.projects
                          .sortBy(_.name)
                          .map { project =>
                            val projectLink = SidebarLink(
                              linkName = project.name,
                              link = router.navigateTo(ShowAllTasksPage(project.id)),
                              collapsed = collapsed,
                              onExportClick = Some(() => dumpProject(project.id)),
                              onConfigClick = Some(() => router.route(ShowAllTaskConfigsPage(project.id))),
                              onImportClick = Some(() => importAllForProject(project.id)),
                              icon = project.icon
                            )
                            if (isProjectSelected(project, page)) projectLink.selectedProject
                            else projectLink.deselectedProject
                          }
                  }
            }
          )
        ),
        button(
          `type`("button"),
          title("Slacker"),
          cls(
            "absolute bottom-44 left-1/2 -translate-x-1/2 inline-flex items-center justify-center w-10 h-10 text-body bg-neutral-secondary-medium border border-default-medium hover:bg-neutral-tertiary-medium rounded-full shadow-lg focus:ring-4 focus:ring-brand-medium focus:outline-none cursor-pointer"
          ),
          TgIcons.slackerIcon("w-5 h-5"),
          span(cls("sr-only"), "Slacker"),
          onClick.mapToUnit --> (_ => onSlacker())
        ),
        button(
          `type`("button"),
          title("Team daily"),
          cls(
            "absolute bottom-32 left-1/2 -translate-x-1/2 inline-flex items-center justify-center w-10 h-10 text-body bg-neutral-secondary-medium border border-default-medium hover:bg-neutral-tertiary-medium rounded-full shadow-lg focus:ring-4 focus:ring-brand-medium focus:outline-none cursor-pointer"
          ),
          TgIcons.teamDailyIcon("w-5 h-5"),
          span(cls("sr-only"), "Team daily"),
          onClick.mapToUnit --> (_ => onTeamDaily())
        ),
        button(
          `type`("button"),
          title("Bulk add tasks"),
          cls(
            "absolute bottom-20 left-1/2 -translate-x-1/2 inline-flex items-center justify-center w-10 h-10 text-body bg-neutral-secondary-medium border border-default-medium hover:bg-neutral-tertiary-medium rounded-full shadow-lg focus:ring-4 focus:ring-brand-medium focus:outline-none cursor-pointer"
          ),
          TgIcons.bulkAddIcon("w-5 h-5"),
          span(cls("sr-only"), "Bulk add tasks"),
          onClick.mapToUnit --> (_ => onBulkCreate())
        ),
        button(
          `type`("button"),
          title("Create task"),
          cls(
            "absolute bottom-4 left-1/2 -translate-x-1/2 inline-flex items-center justify-center w-14 h-14 text-white bg-brand hover:bg-brand-strong rounded-full shadow-lg focus:ring-4 focus:ring-brand-medium focus:outline-none cursor-pointer"
          ),
          TgIcons.plusIcon("w-6 h-6"),
          span(cls("sr-only"), "Create task"),
          onClick.mapToUnit --> (_ => onQuickCreate())
        )
      )
    )
  }

}

object Sidebar {
  def projectIdForPage(page: FrontendPage): Option[UUID] = page match {
    case CreateTaskPage(projectId)           => Some(projectId)
    case CreateTaskStatusPage(projectId)     => Some(projectId)
    case EditTaskStatusPage(projectId, _)    => Some(projectId)
    case ShowAllTasksPage(projectId, _)      => Some(projectId)
    case ShowAllTaskStatusesPage(projectId, _) => Some(projectId)
    case CreateTaskLabelPage(projectId)        => Some(projectId)
    case EditTaskLabelPage(projectId, _)       => Some(projectId)
    case ShowAllTaskLabelsPage(projectId, _)   => Some(projectId)
    case CreateTaskTemplatePage(projectId)     => Some(projectId)
    case EditTaskTemplatePage(projectId, _)    => Some(projectId)
    case ShowAllTaskTemplatesPage(projectId, _) => Some(projectId)
    case EditTaskPage(projectId, _)          => Some(projectId)
    case ShowAllTaskConfigsPage(projectId, _) => Some(projectId)
    case CreateDataExportConfigPage(projectId) => Some(projectId)
    case DataExportStatusPage(projectId)       => Some(projectId)
    case CreateDataImportConfigPage(projectId) => Some(projectId)
    case CreateProjectMappingPage(projectId) => Some(projectId)
    case CreateMilestonePage(projectId)      => Some(projectId)
    case ShowMilestonesPage(projectId, _)    => Some(projectId)
    case ShowMilestoneHistoryPage(projectId, _) => Some(projectId)
    case ShowTimeboxesPage(projectId, _)     => Some(projectId)
    case CreateTimeboxPage(projectId)        => Some(projectId)
    case EditTimeboxPage(projectId, _)       => Some(projectId)
    case TimeboxHistoryPage(projectId, _)    => Some(projectId)
    case TimeboxReviewPage(projectId, _)     => Some(projectId)
    case ShowContributionsPage(projectId, _) => Some(projectId)
    case EditProjectPage(id)                 => Some(id)
    case _                                   => None
  }

  val sideBarLinkStyle =
    "flex items-center px-2 py-1.5 text-body rounded-base hover:bg-neutral-tertiary hover:text-fg-brand group"

  val sidebarSelectedLink =
    "flex items-center px-2 py-1.5 text-body rounded-base bg-neutral-tertiary text-fg-brand group"

  val collapsedGlyphBoxStyle: Seq[String] =
    Seq("w-6", "h-6", "flex", "items-center", "justify-center", "leading-none", "shrink-0")
}
