package com.togger.ui.taskconfig

import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.routes.{FrontendPage, TgRouter}
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
  projectId: UUID
)(implicit
  ioRuntime: IORuntime
) {

  val element = {
    div(
      new ShowAllTaskDumpConfigsComponent(
        taskDumpConfigService,
        taskDumpService,
        tgDateFormater,
        router,
        spinnerService,
        toast,
        projectId
      ).element,
      div(cls("mt-4")),
      new ShowAllTaskLoadConfigsComponent(
        taskLoadConfigService,
        taskDumpService,
        tgDateFormater,
        router,
        spinnerService,
        toast,
        projectId
      ).element
    )
  }

}
