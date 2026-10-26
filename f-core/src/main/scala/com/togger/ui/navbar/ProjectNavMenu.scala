package com.togger.ui.navbar

import com.raquo.laminar.api.L._
import com.togger.dto.organization.OrganizationProjectResp
import com.togger.routes.Pages._
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.ui.organization.{OrganizationWithProjects, TgIcons}
import org.scalajs.dom

import java.util.UUID

case class ProjectNavGroup(title: String, links: Seq[(String, FrontendPage)])

/** The name and icon of a project followed by its links, grouped by topic, as dropdowns in the navbar; shown while a
  * page of a project is open.
  */
class ProjectNavMenu(
  router: TgRouter[FrontendPage],
  organizations: Signal[Seq[OrganizationWithProjects]],
  onExport: UUID => Unit,
  onImport: UUID => Unit
) {

  private val openGroup: Var[Option[String]] = Var(None)

  private val closeGroup = Observer[Unit](_ => openGroup.set(None))

  private def toggle(title: String): Unit =
    openGroup.update(open => if (open.contains(title)) None else Some(title))

  private def groupsOf(projectId: UUID): Seq[ProjectNavGroup] = ProjectNavMenu.groups(projectId)

  private def groupMenu(group: ProjectNavGroup): HtmlElement =
    div(
      cls("relative"),
      button(
        `type`("button"),
        cls(NavbarComponent.navLinkClass, "cursor-pointer"),
        aria.expanded <-- openGroup.signal.map(_.contains(group.title)),
        group.title,
        onClick.stopPropagation.mapToUnit --> (_ => toggle(group.title))
      ),
      child <-- openGroup.signal.map(open => if (open.contains(group.title)) panel(group) else emptyNode)
    )

  private def panel(group: ProjectNavGroup): HtmlElement =
    div(
      cls(NavbarComponent.dropdownPanelClass, "left-0 right-auto"),
      onMountBind { ctx =>
        val rootNode = ctx.thisNode.ref

        documentEvents(_.onClick)
          .filter(event => !rootNode.contains(event.target.asInstanceOf[dom.Node]))
          .mapToUnit --> closeGroup
      },
      onMountBind(_ => documentEvents(_.onKeyDown).filter(_.key == "Escape").mapToUnit --> closeGroup),
      ul(
        cls("p-2 text-sm text-body font-medium"),
        group.links.map { case (label, page) =>
          li(
            a(
              cls(NavbarComponent.dropdownItemClass),
              label,
              router.navigateTo(page),
              onClick.mapToUnit --> closeGroup
            )
          )
        }
      )
    )

  private def directLink(label: String, page: FrontendPage, selected: Boolean): HtmlElement =
    a(
      cls(if (selected) NavbarComponent.selectedNavLinkClass else NavbarComponent.navLinkClass),
      label,
      router.navigateTo(page)
    )

  private def actionButton(label: String, icon: Modifier[HtmlElement], action: () => Unit): HtmlElement =
    button(
      `type`("button"),
      cls(NavbarComponent.navLinkClass, "cursor-pointer"),
      title(label),
      aria.label(label),
      icon,
      onClick.mapToUnit --> (_ => action())
    )

  private def directLinks(projectId: UUID, current: FrontendPage): Seq[HtmlElement] = {
    val contributionsSelected = current match {
      case ShowContributionsPage(id, _) => id == projectId
      case _                            => false
    }
    val syncSelected = current match {
      case SyncConfigPage(id, _) => id == projectId
      case _                     => false
    }
    Seq(
      directLink("Contributions", ShowContributionsPage(projectId), contributionsSelected),
      directLink("Sync", SyncConfigPage(projectId), syncSelected),
      actionButton("Import", TgIcons.uploadIcon("w-4 h-4"), () => onImport(projectId)),
      actionButton("Export", TgIcons.downloadIcon("w-4 h-4"), () => onExport(projectId))
    )
  }

  private val projectSignal: Signal[Option[OrganizationProjectResp]] =
    router.currentPageSignal.combineWith(organizations).map { case (page, orgs) =>
      Sidebar.projectIdForPage(page).flatMap(id => orgs.flatMap(_.projects).find(_.id == id))
    }

  private def projectPrefix(project: OrganizationProjectResp): HtmlElement =
    a(
      cls("flex items-center gap-2 me-2 rounded-base hover:text-fg-brand"),
      span(cls("text-base"), project.icon),
      span(cls("text-sm font-semibold text-heading whitespace-nowrap"), project.name),
      router.navigateTo(ProjectHomePage(project.id))
    )

  val element: HtmlElement =
    div(
      cls("hidden md:flex items-center gap-1"),
      children <-- router.currentPageSignal.combineWith(projectSignal).map { case (page, project) =>
        project.toSeq.map(projectPrefix) ++
          Sidebar.projectIdForPage(page).toSeq.flatMap(id => groupsOf(id).map(groupMenu) ++ directLinks(id, page))
      }
    )
}

object ProjectNavMenu {

  def groups(projectId: UUID): Seq[ProjectNavGroup] = Seq(
    ProjectNavGroup(
      "Tasks",
      Seq(
        "Tasks" -> ShowAllTasksPage(projectId),
        "Create task" -> CreateTaskPage(projectId),
        "Task statuses" -> ShowAllTaskStatusesPage(projectId),
        "Task labels" -> ShowAllTaskLabelsPage(projectId),
        "Task templates" -> ShowAllTaskTemplatesPage(projectId)
      )
    ),
    ProjectNavGroup(
      "Milestones",
      Seq(
        "Milestones" -> ShowMilestonesPage(projectId),
        "Create milestone" -> CreateMilestonePage(projectId),
        "Milestone history" -> ShowMilestoneHistoryPage(projectId)
      )
    ),
    ProjectNavGroup(
      "Timeboxes",
      Seq("Timeboxes" -> ShowTimeboxesPage(projectId), "Create timebox" -> CreateTimeboxPage(projectId))
    )
  )
}
