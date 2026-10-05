package com.togger.ui.projectlabel

import cats.effect.unsafe.IORuntime
import com.togger.common.TgClock
import com.togger.components.button.TgHtmlButton
import com.togger.dto._
import com.togger.style.TgCommonStyleJs.{heightAndWidthFitContent, justifyCenterFlex}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.TgSelect.TgSelectSyntax
import com.togger.ui.auth.{AccountService, TgCard, TgInputType}
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.toast.Toast
import com.raquo.laminar.api.L._
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro

import java.util.UUID
import scala.collection.immutable.ListMap

case class EditProjectLabelForm(
  projectLabelId: UUID = UUID.randomUUID(),
  name: String = "",
  description: String = "",
  categorySelected: String = ""
)

class EditProjectLabelComponent(
  projectLabelService: ProjectLabelFeService,
  projectLabelCategoryService: ProjectLabelCategoryFeService,
  spinnerService: SpinnerService,
  tgClock: TgClock,
  accountService: AccountService,
  projectLabelId: UUID,
  val toast: Toast
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  val formState = Var(EditProjectLabelForm(projectLabelId = projectLabelId))
  val categoriesVar = Var(Seq.empty[FindProjectLabelCategoryResp])

  spinnerService
    .runWithSpinner(projectLabelCategoryService.findAll())
    .map(_.map(success => categoriesVar.set(success.body.get)))
    .unsafeRunAndForget()

  spinnerService
    .runWithSpinner(projectLabelService.findProjectLabelById(projectLabelId))
    .map(
      _.fold(
        error => println(error),
        success => {
          val dto = success.body.get
          formState.set(
            EditProjectLabelForm(
              projectLabelId = dto.id,
              name = dto.name,
              description = dto.description,
              categorySelected = dto.categoryId.toString
            )
          )
        }
      )
    )
    .unsafeRunAndForget()

  lazy val submitter = Observer[EditProjectLabelForm] { form =>
    val dto = UpdateProjectLabelResp(
      id = form.projectLabelId,
      name = form.name,
      description = form.description,
      categoryId = UUID.fromString(form.categorySelected)
    )
    spinnerService
      .runWithSpinner(projectLabelService.updateProjectLabel(dto))
      .toastMessageJustMessage("Project label updated successfully")
      .unsafeRunAndForget()
  }

  private val nameInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.name),
    errors = EventStream.empty,
    labelValue = "Name",
    tgInputType = TgInputType.Text
  )

  private val descriptionInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.description),
    errors = EventStream.empty,
    labelValue = "Description",
    tgInputType = TgInputType.Text
  )

  def renderElement(categories: Seq[FindProjectLabelCategoryResp]) = {
    val categorySelect = formState.tgSelectManualError(
      inputAndUpdater = selectorAndUpdaterMacro(_.categorySelected),
      labelValue = "Category",
      valuesAndDisplays = ListMap.from(categories.sortBy(_.name).map(c => c.id.toString -> c.name))
    )

    div(
      justifyCenterFlex,
      TgCard
        .headerNoFooter(
          header = TgCard.CardHeader.plainText("Edit project label", TgCard.CardHeaderSize.H3),
          body = TgCard.HtmlCardContent(
            div(
              form(
                onSubmit.preventDefault.mapTo(formState.now()) --> submitter,
                nameInput.htmlElement.amend(cls("mb-4")),
                descriptionInput.htmlElement.amend(cls("mb-4")),
                categorySelect.htmlElement.amend(cls("mb-4")),
                TgHtmlButton.update(EventStream.fromValue(false))
              )
            )
          )
        )
        .element
        .amend(heightAndWidthFitContent)
    )
  }

  val element = div(
    child <-- categoriesVar.signal.map(categories => renderElement(categories))
  )

}
