package com.togger.ui.dataexportconfig

import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.components.button.TgHtmlButton
import com.togger.dto.dataexportconfig.UpdateDataExportConfigReq
import com.togger.routes.Pages.EditDataExportConfigPage
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

case class EditDataExportConfigForm(
  id: UUID = UUID.randomUUID(),
  projectId: UUID = UUID.randomUUID(),
  accountId: UUID = UUID.randomUUID(),
  name: String = "",
  exportPath: String = ""
)

class EditDataExportConfigComponent(
  id: UUID,
  dataExportConfigService: DataExportConfigFeService,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  val toast: Toast
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  val formState = Var(EditDataExportConfigForm(id = id))

  spinnerService
    .runWithSpinner(
      dataExportConfigService
        .findById(id)
        .map(
          _.fold(
            error => dom.window.alert("Oops an error: " + error),
            success => {
              val dto = success.body.get
              formState.set(
                EditDataExportConfigForm(
                  id = dto.id,
                  projectId = dto.projectId,
                  accountId = dto.accountId,
                  name = dto.name,
                  exportPath = dto.exportPath
                )
              )
            }
          )
        )
    )
    .unsafeRunAndForget()

  private val submitter = Observer[EditDataExportConfigForm] { state =>
    val dto = UpdateDataExportConfigReq(
      id = state.id,
      projectId = state.projectId,
      name = state.name,
      exportPath = state.exportPath
    )

    spinnerService
      .runWithSpinner(dataExportConfigService.update(dto))
      .toastMessageFn("Export data config updated successfully")(_ =>
        router.route(EditDataExportConfigPage(state.projectId, id))
      )
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
        header = TgCard.CardHeader.plainText("Edit export data config", TgCard.CardHeaderSize.H3),
        body = TgCard.HtmlCardContent(
          div(
            form(
              onSubmit.preventDefault.mapTo(formState.now()) --> submitter,
              nameInput.htmlElement.amend(cls("mb-4 w-[60ch] max-w-full")),
              exportPathInput.htmlElement.amend(cls("mb-4 w-[60ch] max-w-full")),
              div(cls("flex justify-end"), TgHtmlButton.updateSmall(EventStream.fromValue(false)))
            )
          )
        )
      )
      .element
      .amend(heightAndWidthFitContent)
  )

}
