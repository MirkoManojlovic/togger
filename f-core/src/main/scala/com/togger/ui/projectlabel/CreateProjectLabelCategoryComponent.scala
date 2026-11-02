package com.togger.ui.projectlabel

import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.components.button.TgHtmlButton
import com.togger.dto._
import com.togger.style.TgCommonStyleJs.heightAndWidthFitContent
import com.togger.style.TgCommonStyleJs.justifyCenterFlex
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgCard
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.TgInputType
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.toast.Toast
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro

case class CreateProjectLabelCategoryForm(name: String = "")

class CreateProjectLabelCategoryComponent(
  projectLabelCategoryService: ProjectLabelCategoryFeService,
  spinnerService: SpinnerService,
  val toast: Toast
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  val formState = Var(CreateProjectLabelCategoryForm())

  lazy val submitter = Observer[CreateProjectLabelCategoryForm] { form =>
    val dto = CreateProjectLabelCategoryReq(name = form.name)
    spinnerService
      .runWithSpinner(projectLabelCategoryService.createProjectLabelCategory(dto))
      .toastMessageJustMessage("Project label category created successfully")
      .unsafeRunAndForget()
  }

  private val nameInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.name),
    errors = EventStream.empty,
    labelValue = "Name",
    tgInputType = TgInputType.Text
  )

  val element = div(
    justifyCenterFlex,
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText("Create a project label category", TgCard.CardHeaderSize.H3),
        body = TgCard.HtmlCardContent(
          div(
            form(
              onSubmit.preventDefault.mapTo(formState.now()) --> submitter,
              nameInput.htmlElement.amend(cls("mb-4")),
              div(cls("flex justify-end"), TgHtmlButton.createSmall(EventStream.fromValue(false)))
            )
          )
        )
      )
      .element
      .amend(heightAndWidthFitContent)
  )

}
