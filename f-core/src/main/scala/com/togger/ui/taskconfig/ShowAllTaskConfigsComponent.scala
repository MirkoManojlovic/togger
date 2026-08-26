package com.togger.ui.taskconfig

import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.ui.projectmapping.{ProjectMappingFeService, ShowProjectMappingComponent}
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.TaskDumpFeService
import com.togger.ui.taskdumpconfig.{ShowAllTaskDumpConfigsComponent, TaskDumpConfigFeService}
import com.togger.ui.taskloadconfig.{ShowAllTaskLoadConfigsComponent, TaskLoadConfigFeService}
import com.togger.ui.toast.Toast

import java.util.UUID

class ShowAllTaskConfigsComponent(
  taskDumpConfigService: TaskDumpConfigFeService,
  taskDumpService: TaskDumpFeService,
  taskLoadConfigService: TaskLoadConfigFeService,
  projectMappingService: ProjectMappingFeService,
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
        router,
        spinnerService,
        toast,
        projectId
      ).element,
      div(cls("mt-4")),
      new ShowAllTaskLoadConfigsComponent(
        taskLoadConfigService,
        taskDumpService,
        projectMappingService,
        router,
        spinnerService,
        toast,
        projectId
      ).element,
      div(cls("mt-4")),
      new ShowProjectMappingComponent(projectMappingService, router, spinnerService, toast, projectId).element
    )
  }

}
