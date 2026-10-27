package com.togger.ui.dataexportconfig

import com.raquo.laminar.api.L._
import com.togger.dto.{DataExportPathStatusResp, DataExportSyncStatusResp}
import com.togger.ui.components.{TgTab, TgTabs}
import com.togger.ui.organization.TgIcons
import com.togger.ui.task.TaskConfigTableSupport

/** What an export would change, one tab for each export path. Two buttons in the top right corner: one for all the
  * paths, with the number of paths that still need it, and one for the path of the open tab. A path needs the action
  * while some of its entities are not in sync. An import check reuses it with its own button label and its own idea
  * of which entities still need the action.
  */
class ExportCheckComponent(
  pathStatuses: Seq[DataExportPathStatusResp],
  onAll: List[DataExportPathStatusResp] => Unit,
  onPath: DataExportPathStatusResp => Unit,
  actionLabel: String = "Export",
  statusTitle: String = "Export status",
  needsAction: DataExportSyncStatusResp => Boolean = _ != DataExportSyncStatusResp.InSync
) {

  import TaskConfigTableSupport._

  private def pathNeedsAction(pathStatus: DataExportPathStatusResp): Boolean =
    pathStatus.entityStatuses.exists(entity => needsAction(entity.status))

  private val pathsToAct: List[DataExportPathStatusResp] = pathStatuses.filter(pathNeedsAction).toList

  private def tabId(index: Int): String = s"export-path-$index"

  private def pathTab(pathStatus: DataExportPathStatusResp, index: Int) =
    TgTab(
      tabId(index),
      pathStatus.name.getOrElse(pathStatus.exportPath),
      exportPathStatusCard(pathStatus, s"$statusTitle: ${pathStatus.exportPath}"),
      tooltip = pathStatus.name.map(_ => pathStatus.exportPath)
    )

  private val selectedTabVar = Var(tabId(0))

  private val buttonCls =
    "inline-flex items-center justify-center px-3 py-2 text-sm font-medium text-white bg-brand " +
      "hover:bg-brand-strong rounded-base focus:ring-4 focus:ring-brand-medium focus:outline-none " +
      "disabled:opacity-40 disabled:cursor-not-allowed"

  private val selectedPath: Signal[Option[DataExportPathStatusResp]] =
    selectedTabVar.signal.map(id => pathStatuses.zipWithIndex.collectFirst { case (path, index) if tabId(index) == id => path })

  private val allButton =
    button(
      `type`("button"),
      title(if (pathsToAct.nonEmpty) s"$actionLabel all paths" else "Everything is in sync"),
      TgIcons.downloadIcon(className = "w-4 h-4 me-2"),
      s"$actionLabel all",
      span(
        cls("ms-2 px-1.5 py-0.5 text-xs font-semibold rounded-full bg-white text-fg-brand"),
        title(s"${pathsToAct.size} path(s) will be affected"),
        pathsToAct.size.toString
      ),
      cls(buttonCls),
      disabled := pathsToAct.isEmpty,
      onClick.mapToUnit --> (_ => onAll(pathsToAct))
    )

  private val pathButton =
    button(
      `type`("button"),
      title <-- selectedPath.map(_.fold(actionLabel)(path => s"$actionLabel ${path.exportPath}")),
      TgIcons.downloadIcon(className = "w-4 h-4 me-2"),
      child.text <-- selectedPath.map(path => s"$actionLabel ${path.flatMap(_.name).getOrElse("this path")}"),
      cls(buttonCls),
      disabled <-- selectedPath.map(!_.exists(pathNeedsAction)),
      onClick.compose(_.sample(selectedPath).collect { case Some(path) => path }) --> onPath
    )

  private val paths: HtmlElement =
    pathStatuses match {
      case Seq()       => div(cls("text-body"), "No export paths configured for this project")
      case Seq(single) =>
        div(
          cls("flex flex-col gap-3"),
          p(
            cls("text-base font-semibold text-heading"),
            single.name.map(_ => title(single.exportPath)),
            single.name.fold(single.exportPath)(identity): String
          ),
          exportPathStatusCard(single, s"$statusTitle: ${single.exportPath}")
        )
      case several     => new TgTabs(several.zipWithIndex.map((pathTab _).tupled), selectedTabVar).element
    }

  val element =
    div(
      cls("p-4 flex flex-col gap-4"),
      div(cls("flex justify-end gap-2"), pathButton, allButton),
      paths
    )

}
