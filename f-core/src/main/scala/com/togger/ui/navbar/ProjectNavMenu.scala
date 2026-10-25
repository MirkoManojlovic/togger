package com.togger.ui.navbar

import com.raquo.laminar.api.L._
import com.togger.routes.Pages._
import com.togger.routes.{FrontendPage, TgRouter}
import org.scalajs.dom

import java.util.UUID

case class ProjectNavGroup(title: String, links: Seq[(String, FrontendPage)])

/** The links of a project, grouped by topic, as dropdowns in the navbar; shown while a page of a project is open. */
class ProjectNavMenu(router: TgRouter[FrontendPage]) {

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

  val element: HtmlElement =
    div(
      cls("hidden md:flex items-center gap-1"),
      children <-- router.currentPageSignal.map { page =>
        Sidebar.projectIdForPage(page).toSeq.flatMap(groupsOf).map(groupMenu)
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
