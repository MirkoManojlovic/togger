package com.togger.ui.synccheck

import com.raquo.laminar.api.L._
import com.togger.dto.DataImportPathResp
import com.togger.dto.DataImportStatusResp
import com.togger.dto.DataSyncProjectResp
import com.togger.dto.DataSyncResp
import com.togger.ui.components.TgTab
import com.togger.ui.components.TgTabs
import com.togger.ui.dataexportconfig.ExportResultsComponent
import com.togger.ui.dataimportconfig.ImportResultsComponent

/** What a sync did, in two tabs: the imports (run first) and the exports, each listing the projects. */
class SyncResultsComponent(response: DataSyncResp) {

  private def importFailures(project: DataSyncProjectResp): Int =
    project.imports.map(failuresOf).sum

  private def failuresOf(path: DataImportPathResp): Int =
    path.error.size + path.result.fold(0)(_.results.count(_.status == DataImportStatusResp.Failed))

  private def exportFailures(project: DataSyncProjectResp): Int =
    project.exportError.size + project.exportResult.fold(0)(_.results.count(_.error.isDefined))

  private def message(text: String) = div(cls("text-body"), text)

  private def importPathPanel(path: DataImportPathResp): HtmlElement =
    path.error match {
      case Some(error) => div(cls("text-fg-danger"), s"Importing from this path failed: $error")
      case None =>
        path.result
          .filter(_.results.nonEmpty)
          .fold[HtmlElement](message("Nothing to import from this path"))(r =>
            new ImportResultsComponent(r.results).element
          )
    }

  private def importPanel(project: DataSyncProjectResp): HtmlElement =
    project.imports match {
      case Nil         => message("No import paths configured for this project")
      case path :: Nil => importPathPanel(path)
      case several =>
        val tabs = several.zipWithIndex.map { case (path, index) =>
          TgTab(s"import-${project.projectId}-$index", path.importPath, importPathPanel(path))
        }
        new TgTabs(tabs, Var(tabs.head.id)).element
    }

  private def exportPanel(project: DataSyncProjectResp): HtmlElement =
    project.exportError match {
      case Some(error) => div(cls("text-fg-danger"), s"Exporting this project failed: $error")
      case None =>
        project.exportResult.fold[HtmlElement](message("No export path configured for this project"))(r =>
          new ExportResultsComponent(r).element
        )
    }

  private def perProject(
    kind: String,
    panel: DataSyncProjectResp => HtmlElement,
    failures: DataSyncProjectResp => Int
  ): HtmlElement =
    if (response.projects.isEmpty) message("No projects to sync")
    else
      div(
        cls("space-y-4"),
        response.projects.map { project =>
          val failed = failures(project)
          div(
            h3(
              cls("mb-2 text-sm font-semibold text-heading"),
              project.projectName,
              if (failed > 0) span(cls("ms-2 font-normal text-fg-danger"), s"$failed failed") else emptyNode
            ),
            div(idAttr(s"sync-$kind-${project.projectId}"), panel(project))
          )
        }
      )

  val element: HtmlElement = {
    val tabs = List(
      TgTab(
        "sync-import",
        "Import",
        perProject("import", importPanel, importFailures),
        badge = Some(Val(response.projects.map(importFailures).sum))
      ),
      TgTab(
        "sync-export",
        "Export",
        perProject("export", exportPanel, exportFailures),
        badge = Some(Val(response.projects.map(exportFailures).sum))
      )
    )
    div(cls("p-4"), new TgTabs(tabs, Var(tabs.head.id)).element)
  }

}
