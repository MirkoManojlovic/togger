package com.togger.ui.dataexportconfig

import com.raquo.laminar.api.L._
import com.togger.dto.{DataExportPathStatusResp, DataExportSyncStatusResp}
import com.togger.ui.components.{TgTab, TgTabs}
import com.togger.ui.organization.TgIcons
import com.togger.ui.task.TaskConfigTableSupport

/** What an export would change, one tab for each export path, with the export button in the top right corner. The
  * button is enabled only while some entity is not in sync with the disk. An import check reuses it with its own
  * button label and its own idea of which entities still need the action.
  */
class ExportCheckComponent(
  pathStatuses: Seq[DataExportPathStatusResp],
  onExport: () => Unit,
  actionLabel: String = "Export",
  statusTitle: String = "Export status",
  needsAction: DataExportSyncStatusResp => Boolean = _ != DataExportSyncStatusResp.InSync
) {

  import TaskConfigTableSupport._

  private val needsExport: Boolean =
    pathStatuses.exists(_.entityStatuses.exists(entity => needsAction(entity.status)))

  private def tabId(index: Int): String = s"export-path-$index"

  private def pathTab(pathStatus: DataExportPathStatusResp, index: Int) =
    TgTab(tabId(index), pathStatus.exportPath, exportPathStatusCard(pathStatus, s"$statusTitle: ${pathStatus.exportPath}"))

  private val selectedTabVar = Var(tabId(0))

  private val exportButton =
    button(
      `type`("button"),
      title(if (needsExport) actionLabel else "Everything is in sync"),
      TgIcons.downloadIcon(className = "w-4 h-4 me-2"),
      actionLabel,
      cls(
        "inline-flex items-center justify-center px-3 py-2 text-sm font-medium text-white bg-brand " +
          "hover:bg-brand-strong rounded-base focus:ring-4 focus:ring-brand-medium focus:outline-none " +
          "disabled:opacity-40 disabled:cursor-not-allowed"
      ),
      disabled := !needsExport,
      onClick.mapToUnit --> (_ => onExport())
    )

  private val paths: HtmlElement =
    pathStatuses match {
      case Seq()       => div(cls("text-body"), "No export paths configured for this project")
      case Seq(single) => exportPathStatusCard(single, s"$statusTitle: ${single.exportPath}")
      case several     => new TgTabs(several.zipWithIndex.map((pathTab _).tupled), selectedTabVar).element
    }

  val element =
    div(
      cls("p-4 flex flex-col gap-4"),
      div(cls("flex justify-end"), exportButton),
      paths
    )

}
