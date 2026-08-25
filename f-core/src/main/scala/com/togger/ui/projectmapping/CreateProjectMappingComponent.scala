package com.togger.ui.projectmapping

import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.components.button.TgHtmlButton
import com.togger.dto.projectmapping.CreateProjectMappingReq
import com.togger.routes.Pages.ShowAllTaskConfigsPage
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.style.TgCommonStyleJs.{heightAndWidthFitContent, justifyCenterFlex}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.{FormErrors, TgCard, TgInputType}
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.toast.Toast
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro

import java.util.UUID
import scala.util.Try

case class CreateProjectMappingForm(
  projectIdDump: String = ""
) extends FormErrors {

  def projectIdDumpValid: Seq[String] =
    Try(UUID.fromString(projectIdDump)).fold(_ => Seq("Must be a valid UUID"), _ => Seq.empty)

  override def additionalErrors: Seq[String] = projectIdDumpValid
}

class CreateProjectMappingComponent(
  projectMappingService: ProjectMappingFeService,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  projectIdDb: UUID,
  val toast: Toast
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  val formState = Var(CreateProjectMappingForm())

  lazy val createSubmitter = Observer[CreateProjectMappingForm] { state =>
    val request = CreateProjectMappingReq(
      projectIdDump = UUID.fromString(state.projectIdDump),
      projectIdDb = projectIdDb
    )
    spinnerService
      .runWithSpinner(projectMappingService.create(request))
      .toastMessageFn("Project mapping created successfully")(_ => router.route(ShowAllTaskConfigsPage(projectIdDb)))
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
        header = TgCard.CardHeader.plainText("Create a project mapping", TgCard.CardHeaderSize.H3),
        body = TgCard.HtmlCardContent(
          div(
            p(cls("mb-4"), s"Db project id: $projectIdDb"),
            form(
              onSubmit.preventDefault.mapTo(formState.now()) --> createSubmitter,
              projectIdDumpInput.htmlElement.amend(cls("mb-4")),
              TgHtmlButton.create(formState.signal.changes.map(_.hasErrors))
            )
          )
        )
      )
      .element
      .amend(heightAndWidthFitContent)
  )

}
