package com.togger.ui.taskconfig

import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.routes.Pages.ShowAllTaskConfigsPage
import com.togger.routes.{FrontendPage, TaskConfigListParams, TgRouter}
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.{DataExportFeService, TgDateFormater}
import com.togger.ui.dataexportconfig.{ShowAllDataExportConfigsComponent, DataExportConfigFeService}
import com.togger.ui.taskloadconfig.{ShowAllTaskLoadConfigsComponent, TaskLoadConfigFeService}
import com.togger.ui.toast.Toast

import java.util.UUID

class ShowAllTaskConfigsComponent(
  dataExportConfigService: DataExportConfigFeService,
  dataExportService: DataExportFeService,
  taskLoadConfigService: TaskLoadConfigFeService,
  tgDateFormater: TgDateFormater,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  toast: Toast,
  projectId: UUID,
  params: TaskConfigListParams
)(implicit
  ioRuntime: IORuntime
) {

  private def showLists(newParams: TaskConfigListParams): Unit =
    router.route(ShowAllTaskConfigsPage(projectId, newParams))

  val element = {
    div(
      new ShowAllDataExportConfigsComponent(
        dataExportConfigService,
        dataExportService,
        tgDateFormater,
        router,
        spinnerService,
        toast,
        projectId,
        params.export,
        (newPage, newSize) => showLists(params.exportAtPage(newPage, newSize))
      ).element,
      div(cls("mt-4")),
      new ShowAllTaskLoadConfigsComponent(
        taskLoadConfigService,
        dataExportService,
        tgDateFormater,
        router,
        spinnerService,
        toast,
        projectId,
        params.load,
        (newPage, newSize) => showLists(params.loadAtPage(newPage, newSize))
      ).element
    )
  }

}
