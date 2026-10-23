package com.togger.ui.projectlabel

import cats.effect.unsafe.IORuntime
import com.togger.common.TgClock
import com.togger.components.button.TgHtmlButton
import com.togger.dto._
import com.togger.style.TgCommonStyleJs.{heightAndWidthFitContent, justifyCenterFlex}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.{AccountService, TgCard, TgInputType}
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.toast.Toast
import com.raquo.laminar.api.L._
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro

import java.util.UUID

case class EditProjectLabelCategoryForm(
  projectLabelCategoryId: UUID = UUID.randomUUID(),
  name: String = ""
)

class EditProjectLabelCategoryComponent(
  projectLabelCategoryService: ProjectLabelCategoryFeService,
  spinnerService: SpinnerService,
  tgClock: TgClock,
  accountService: AccountService,
  projectLabelCategoryId: UUID,
  val toast: Toast
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  val formState = Var(EditProjectLabelCategoryForm(projectLabelCategoryId = projectLabelCategoryId))

  spinnerService
    .runWithSpinner(projectLabelCategoryService.findProjectLabelCategoryById(projectLabelCategoryId))
    .map(
      _.fold(
        error => println(error),
        success => {
          val dto = success.body.get
          formState.set(
            EditProjectLabelCategoryForm(
              projectLabelCategoryId = dto.id,
              name = dto.name
            )
          )
        }
      )
    )
    .unsafeRunAndForget()

  lazy val submitter = Observer[EditProjectLabelCategoryForm] { form =>
    val dto = UpdateProjectLabelCategoryResp(id = form.projectLabelCategoryId, name = form.name)
    spinnerService
      .runWithSpinner(projectLabelCategoryService.updateProjectLabelCategory(dto))
      .toastMessageJustMessage("Project label category updated successfully")
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
        header = TgCard.CardHeader.plainText("Edit project label category", TgCard.CardHeaderSize.H3),
        body = TgCard.HtmlCardContent(
          div(
            form(
              onSubmit.preventDefault.mapTo(formState.now()) --> submitter,
              nameInput.htmlElement.amend(cls("mb-4")),
              div(cls("flex justify-end"), TgHtmlButton.updateSmall(EventStream.fromValue(false)))
            )
          )
        )
      )
      .element
      .amend(heightAndWidthFitContent)
  )

}
