package com.togger.ui.taskconfig

import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.routes.Pages.ShowAllTaskConfigsPage
import com.togger.routes.{FrontendPage, TaskConfigListParams, TgRouter}
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.{TaskDumpFeService, TgDateFormater}
import com.togger.ui.taskdumpconfig.{ShowAllTaskDumpConfigsComponent, TaskDumpConfigFeService}
import com.togger.ui.taskloadconfig.{ShowAllTaskLoadConfigsComponent, TaskLoadConfigFeService}
import com.togger.ui.toast.Toast

import java.util.UUID

class ShowAllTaskConfigsComponent(
  taskDumpConfigService: TaskDumpConfigFeService,
  taskDumpService: TaskDumpFeService,
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
      new ShowAllTaskDumpConfigsComponent(
        taskDumpConfigService,
        taskDumpService,
        tgDateFormater,
        router,
        spinnerService,
        toast,
        projectId,
        params.dump,
        (newPage, newSize) => showLists(params.dumpAtPage(newPage, newSize))
      ).element,
      div(cls("mt-4")),
      new ShowAllTaskLoadConfigsComponent(
        taskLoadConfigService,
        taskDumpService,
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
