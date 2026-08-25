package com.togger.ui.taskloadconfig

import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.components.button.TgHtmlButton
import com.togger.dto.taskloadconfig.UpdateTaskLoadConfigReq
import com.togger.routes.Pages.EditTaskLoadConfigPage
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

case class EditTaskLoadConfigForm(
  id: UUID = UUID.randomUUID(),
  projectId: UUID = UUID.randomUUID(),
  accountId: UUID = UUID.randomUUID(),
  name: String = "",
  dumpPath: String = ""
)

class EditTaskLoadConfigComponent(
  id: UUID,
  taskLoadConfigService: TaskLoadConfigFeService,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  val toast: Toast
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  val formState = Var(EditTaskLoadConfigForm(id = id))

  spinnerService
    .runWithSpinner(
      taskLoadConfigService
        .findById(id)
        .map(
          _.fold(
            error => dom.window.alert("Oops an error: " + error),
            success => {
              val dto = success.body.get
              formState.set(
                EditTaskLoadConfigForm(
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

  val submitter = Observer[EditTaskLoadConfigForm] { state =>
    val dto = UpdateTaskLoadConfigReq(
      id = state.id,
      name = state.name,
      projectId = state.projectId,
      dumpPath = state.dumpPath
    )

    spinnerService
      .runWithSpinner(taskLoadConfigService.update(dto))
      .toastMessageFn("Task load config updated successfully")(_ => router.route(EditTaskLoadConfigPage(id)))
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
    labelValue = "Project dump root path (must be an abs path; the latest dump inside it is loaded)",
    tgInputType = TgInputType.Text
  )

  val element = div(
    justifyCenterFlex,
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText("Edit task load config", TgCard.CardHeaderSize.H3),
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
