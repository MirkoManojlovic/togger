package com.togger.ui.dataimportconfig

import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.components.button.TgHtmlButton
import com.togger.dto.dataimportconfig.CreateDataImportConfigReq
import com.togger.style.TgCommonStyleJs.{heightAndWidthFitContent, justifyCenterFlex}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.{TgCard, TgInputType}
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.toast.Toast
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro

import java.util.UUID

case class CreateDataImportConfigForm(
  name: String = "",
  importPath: String = ""
)

class CreateDataImportConfigComponent(
  dataImportConfigService: DataImportConfigFeService,
  spinnerService: SpinnerService,
  projectId: UUID,
  val toast: Toast
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  val formState = Var(CreateDataImportConfigForm())

  lazy val createSubmitter = Observer[CreateDataImportConfigForm] { state =>
    val request = CreateDataImportConfigReq(
      name = state.name,
      projectId = projectId,
      importPath = state.importPath
    )
    spinnerService
      .runWithSpinner(dataImportConfigService.create(request))
      .toastMessageJustMessage("Import data config created successfully")
      .unsafeRunAndForget()
  }

  private val nameInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.name),
    errors = EventStream.empty,
    labelValue = "Name",
    tgInputType = TgInputType.Text
  )

  private val importPathInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.importPath),
    errors = EventStream.empty,
    labelValue = "Import path (must be an abs path; the latest dump inside it is imported)",
    tgInputType = TgInputType.Text
  )

  val element = div(
    justifyCenterFlex,
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText("Create a Import data config", TgCard.CardHeaderSize.H3),
        body = TgCard.HtmlCardContent(
          div(
            form(
              onSubmit.preventDefault.mapTo(formState.now()) --> createSubmitter,
              nameInput.htmlElement.amend(cls("mb-4")),
              importPathInput.htmlElement.amend(cls("mb-4")),
              TgHtmlButton.create(EventStream.fromValue(false))
            )
          )
        )
      )
      .element
      .amend(heightAndWidthFitContent)
  )

}
