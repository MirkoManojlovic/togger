package com.togger.ui.projectmapping

import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.components.button.TgHtmlButton
import com.togger.dto.projectmapping.UpdateProjectMappingReq
import com.togger.routes.Pages.EditProjectMappingPage
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.style.TgCommonStyleJs.{heightAndWidthFitContent, justifyCenterFlex}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.{FormErrors, TgCard, TgInputType}
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.toast.Toast
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro
import org.scalajs.dom

import java.util.UUID
import scala.util.Try

case class EditProjectMappingForm(
  id: UUID = UUID.randomUUID(),
  projectIdDump: String = "",
  projectIdDb: UUID = UUID.randomUUID()
) extends FormErrors {

  def projectIdDumpValid: Seq[String] =
    Try(UUID.fromString(projectIdDump)).fold(_ => Seq("Must be a valid UUID"), _ => Seq.empty)

  override def additionalErrors: Seq[String] = projectIdDumpValid
}

class EditProjectMappingComponent(
  id: UUID,
  projectMappingService: ProjectMappingFeService,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  val toast: Toast
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  val formState = Var(EditProjectMappingForm(id = id))

  spinnerService
    .runWithSpinner(
      projectMappingService
        .findById(id)
        .map(
          _.fold(
            error => dom.window.alert("Oops an error: " + error),
            success => {
              val dto = success.body.get
              formState.set(
                EditProjectMappingForm(
                  id = dto.id,
                  projectIdDump = dto.projectIdDump.toString,
                  projectIdDb = dto.projectIdDb
                )
              )
            }
          )
        )
    )
    .unsafeRunAndForget()

  val submitter = Observer[EditProjectMappingForm] { state =>
    val dto = UpdateProjectMappingReq(
      id = state.id,
      projectIdDump = UUID.fromString(state.projectIdDump),
      projectIdDb = state.projectIdDb
    )

    spinnerService
      .runWithSpinner(projectMappingService.update(dto))
      .toastMessageFn("Project mapping updated successfully")(_ => router.route(EditProjectMappingPage(id)))
      .unsafeRunAndForget()
  }

  private val projectIdDumpInput = formState.tgInputManualErrorLP(
    inputAndUpdater = selectorAndUpdaterMacro(_.projectIdDump),
    errorsFn = _.projectIdDumpValid,
    labelValue = "Dump project id (from dump file)",
    tgInputType = TgInputType.Text
  )

  val element = div(
    justifyCenterFlex,
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText("Edit project mapping", TgCard.CardHeaderSize.H3),
        body = TgCard.HtmlCardContent(
          div(
            child <-- formState.signal.map(state => p(cls("mb-4"), s"Db project id: ${state.projectIdDb}")),
            form(
              onSubmit.preventDefault.mapTo(formState.now()) --> submitter,
              projectIdDumpInput.htmlElement.amend(cls("mb-4")),
              TgHtmlButton.update(formState.signal.changes.map(_.hasErrors))
            )
          )
        )
      )
      .element
      .amend(heightAndWidthFitContent)
  )

}
