package com.togger.ui.taskdumpconfig

import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.components.button.TgHtmlButton
import com.togger.dto.taskdumpconfig.UpdateTaskDumpConfigReq
import com.togger.routes.Pages.EditTaskDumpConfigPage
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.style.TgCommonStyleJs.{heightAndWidthFitContent, justifyCenterFlex}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.{TgCard, TgInputType}
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.toast.Toast
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro
import org.scalajs.dom

import java.util.UUID

case class EditTaskDumpConfigForm(
  id: UUID = UUID.randomUUID(),
  projectId: UUID = UUID.randomUUID(),
  accountId: UUID = UUID.randomUUID(),
  name: String = "",
  dumpPath: String = ""
)

class EditTaskDumpConfigComponent(
  id: UUID,
  taskDumpConfigService: TaskDumpConfigFeService,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  val toast: Toast
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  val formState = Var(EditTaskDumpConfigForm(id = id))

  spinnerService
    .runWithSpinner(
      taskDumpConfigService
        .findById(id)
        .map(
          _.fold(
            error => dom.window.alert("Oops an error: " + error),
            success => {
              val dto = success.body.get
              formState.set(
                EditTaskDumpConfigForm(
                  id = dto.id,
                  projectId = dto.projectId,
                  accountId = dto.accountId,
                  name = dto.name,
                  dumpPath = dto.dumpPath
                )
              )
            }
          )
        )
    )
    .unsafeRunAndForget()

  val submitter = Observer[EditTaskDumpConfigForm] { state =>
    val dto = UpdateTaskDumpConfigReq(
      id = state.id,
      projectId = state.projectId,
      name = state.name,
      dumpPath = state.dumpPath
    )

    spinnerService
      .runWithSpinner(taskDumpConfigService.update(dto))
      .toastMessageFn("Task dump config updated successfully")(_ => router.route(EditTaskDumpConfigPage(id)))
      .unsafeRunAndForget()
  }

  private val nameInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.name),
    errors = EventStream.empty,
    labelValue = "Name",
    tgInputType = TgInputType.Text
  )

  private val dumpPathInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.dumpPath),
    errors = EventStream.empty,
    labelValue = "Dump path (must be an abs path)",
    tgInputType = TgInputType.Text
  )

  val element = div(
    justifyCenterFlex,
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText("Edit task dump config", TgCard.CardHeaderSize.H3),
        body = TgCard.HtmlCardContent(
          div(
            form(
              onSubmit.preventDefault.mapTo(formState.now()) --> submitter,
              nameInput.htmlElement.amend(cls("mb-4")),
              dumpPathInput.htmlElement.amend(cls("mb-4")),
              TgHtmlButton.update(EventStream.fromValue(false))
            )
          )
        )
      )
      .element
      .amend(heightAndWidthFitContent)
  )

}
