package com.togger.ui.techstack

import cats.effect.unsafe.IORuntime
import com.togger.common.TgClock
import com.togger.components.button.TgHtmlButton
import com.togger.dto._
import com.togger.style.TgCommonStyleJs.{heightAndWidthFitContent, justifyCenterFlex}
import com.togger.techstack.TechStackCategory
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.TgSelect.TgSelectSyntax
import com.togger.ui.auth.{AccountService, TgCard, TgInputType}
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.toast.Toast
import com.raquo.laminar.api.L._
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro

import java.util.UUID

case class EditTechStackForm(
  techStackId: UUID = UUID.randomUUID(),
  name: String = "",
  description: String = "",
  categorySelected: String = TechStackCategory.Language.value.toString
)

class EditTechStackComponent(
  techStackService: TechStackFeService,
  spinnerService: SpinnerService,
  tgClock: TgClock,
  accountService: AccountService,
  techStackId: UUID,
  val toast: Toast
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  val formState = Var(EditTechStackForm(techStackId = techStackId))

  spinnerService
    .runWithSpinner(techStackService.findTechStackById(techStackId))
    .map(
      _.fold(
        error => println(error),
        success => {
          val dto = success.body.get
          formState.set(
            EditTechStackForm(
              techStackId = dto.id,
              name = dto.name,
              description = dto.description,
              categorySelected = dto.category.value.toString
            )
          )
        }
      )
    )
    .unsafeRunAndForget()

  lazy val submitter = Observer[EditTechStackForm] { form =>
    val dto = UpdateTechStackResp(
      id = form.techStackId,
      name = form.name,
      description = form.description,
      category = TechStackCategory.withValue(form.categorySelected.toInt)
    )
    spinnerService
      .runWithSpinner(techStackService.updateTechStack(dto))
      .toastMessageJustMessage("Tech stack updated successfully")
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

  private val categorySelect = formState.tgSelectManualError(
    inputAndUpdater = selectorAndUpdaterMacro(_.categorySelected),
    labelValue = "Category",
    valuesAndDisplays = TechStackCategory.namesToValuesMapString
  )

  val element = div(
    justifyCenterFlex,
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText("Edit tech stack", TgCard.CardHeaderSize.H3),
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
