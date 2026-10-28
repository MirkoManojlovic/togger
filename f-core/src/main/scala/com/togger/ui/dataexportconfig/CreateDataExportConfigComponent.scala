package com.togger.ui.dataexportconfig

import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.components.button.TgHtmlButton
import com.togger.dto.dataexportconfig.CreateDataExportConfigReq
import com.togger.style.TgCommonStyleJs.{heightAndWidthFitContent, justifyCenterFlex}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.{TgCard, TgInputType}
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.toast.Toast
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro

import java.util.UUID

case class CreateDataExportConfigForm(
  name: String = "",
  exportPath: String = ""
)

class CreateDataExportConfigComponent(
  dataExportConfigService: DataExportConfigFeService,
  spinnerService: SpinnerService,
  projectId: UUID,
  val toast: Toast
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  val formState = Var(CreateDataExportConfigForm())

  lazy val createSubmitter = Observer[CreateDataExportConfigForm] { state =>
    val request = CreateDataExportConfigReq(
      projectId = projectId,
      name = state.name,
      exportPath = state.exportPath
    )
    spinnerService
      .runWithSpinner(dataExportConfigService.create(request))
      .toastMessageJustMessage("Export data config created successfully")
      .unsafeRunAndForget()
  }

  private val nameInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.name),
    errors = EventStream.empty,
    labelValue = "Name",
    tgInputType = TgInputType.Text
  )

  private val exportPathInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.exportPath),
    errors = EventStream.empty,
    labelValue = "Export path (must be an abs path)",
    tgInputType = TgInputType.Text
  )

  val element = div(
    justifyCenterFlex,
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText("Create an Export data config", TgCard.CardHeaderSize.H3),
        body = TgCard.HtmlCardContent(
          div(
            form(
              onSubmit.preventDefault.mapTo(formState.now()) --> createSubmitter,
              nameInput.htmlElement.amend(cls("mb-4 w-[60ch] max-w-full")),
              exportPathInput.htmlElement.amend(cls("mb-4 w-[60ch] max-w-full")),
              div(cls("flex justify-end"), TgHtmlButton.createSmall(EventStream.fromValue(false)))
            )
          )
        )
      )
      .element
      .amend(heightAndWidthFitContent)
  )

}
