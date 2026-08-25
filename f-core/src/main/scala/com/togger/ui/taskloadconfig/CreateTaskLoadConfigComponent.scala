package com.togger.ui.taskloadconfig

import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.components.button.TgHtmlButton
import com.togger.dto.taskloadconfig.CreateTaskLoadConfigReq
import com.togger.style.TgCommonStyleJs.{heightAndWidthFitContent, justifyCenterFlex}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.{TgCard, TgInputType}
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.toast.Toast
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro

import java.util.UUID

case class CreateTaskLoadConfigForm(
  name: String = "",
  dumpPath: String = ""
)

class CreateTaskLoadConfigComponent(
  taskLoadConfigService: TaskLoadConfigFeService,
  spinnerService: SpinnerService,
  projectId: UUID,
  val toast: Toast
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  val formState = Var(CreateTaskLoadConfigForm())

  lazy val createSubmitter = Observer[CreateTaskLoadConfigForm] { state =>
    val request = CreateTaskLoadConfigReq(
      name = state.name,
      projectId = projectId,
      dumpPath = state.dumpPath
    )
    spinnerService
      .runWithSpinner(taskLoadConfigService.create(request))
      .toastMessageJustMessage("Task load config created successfully")
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
        header = TgCard.CardHeader.plainText("Create a task load config", TgCard.CardHeaderSize.H3),
        body = TgCard.HtmlCardContent(
          div(
            form(
              onSubmit.preventDefault.mapTo(formState.now()) --> createSubmitter,
              nameInput.htmlElement.amend(cls("mb-4")),
              dumpPathInput.htmlElement.amend(cls("mb-4")),
              TgHtmlButton.create(EventStream.fromValue(false))
            )
          )
        )
      )
      .element
      .amend(heightAndWidthFitContent)
  )

}
