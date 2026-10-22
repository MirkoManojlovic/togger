package com.togger.ui.dataimportconfig

import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.components.button.TgHtmlButton
import com.togger.dto.dataimportconfig.UpdateDataImportConfigReq
import com.togger.routes.Pages.EditDataImportConfigPage
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

case class EditDataImportConfigForm(
  id: UUID = UUID.randomUUID(),
  projectId: UUID = UUID.randomUUID(),
  accountId: UUID = UUID.randomUUID(),
  name: String = "",
  importPath: String = ""
)

class EditDataImportConfigComponent(
  id: UUID,
  dataImportConfigService: DataImportConfigFeService,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  val toast: Toast
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  val formState = Var(EditDataImportConfigForm(id = id))

  spinnerService
    .runWithSpinner(
      dataImportConfigService
        .findById(id)
        .map(
          _.fold(
            error => dom.window.alert("Oops an error: " + error),
            success => {
              val dto = success.body.get
              formState.set(
                EditDataImportConfigForm(
                  id = dto.id,
                  projectId = dto.projectId,
                  accountId = dto.accountId,
                  name = dto.name,
                  importPath = dto.importPath
                )
              )
            }
          )
        )
    )
    .unsafeRunAndForget()

  val submitter = Observer[EditDataImportConfigForm] { state =>
    val dto = UpdateDataImportConfigReq(
      id = state.id,
      name = state.name,
      projectId = state.projectId,
      importPath = state.importPath
    )

    spinnerService
      .runWithSpinner(dataImportConfigService.update(dto))
      .toastMessageFn("Import data config updated successfully")(_ => router.route(EditDataImportConfigPage(id)))
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
        header = TgCard.CardHeader.plainText("Edit Import data config", TgCard.CardHeaderSize.H3),
        body = TgCard.HtmlCardContent(
          div(
            form(
              onSubmit.preventDefault.mapTo(formState.now()) --> submitter,
              nameInput.htmlElement.amend(cls("mb-4")),
              importPathInput.htmlElement.amend(cls("mb-4")),
              TgHtmlButton.update(EventStream.fromValue(false))
            )
          )
        )
      )
      .element
      .amend(heightAndWidthFitContent)
  )

}
