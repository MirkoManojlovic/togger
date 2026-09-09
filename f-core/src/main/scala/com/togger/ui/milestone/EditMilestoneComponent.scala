package com.togger.ui.milestone

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.implicits._
import com.raquo.laminar.api.L._
import com.togger.components.button.TgHtmlButton
import com.togger.dto.{UpdateMilestoneDateReq, UpdateMilestoneReq}
import com.togger.style.TgCommonStyleJs.{heightAndWidthFitContent, justifyCenterFlex}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.TgTextArea.TgTextAreaSyntax
import com.togger.ui.auth.{TgCard, TgInputType}
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.toast.Toast
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro

import java.util.UUID

case class EditMilestoneForm(
  id: UUID = UUID.randomUUID(),
  name: String = "",
  description: String = ""
)

class EditMilestoneComponent(
  milestoneService: MilestoneFeService,
  milestoneDateService: MilestoneDateFeService,
  spinnerService: SpinnerService,
  milestoneId: UUID,
  val toast: Toast
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  val formState = Var(EditMilestoneForm(id = milestoneId))
  val milestoneDatesVar = Var(Seq.empty[EditMilestoneDateComponent])

  spinnerService
    .runWithSpinner(milestoneService.findMilestoneById(milestoneId))
    .map(
      _.fold(
        error => println(error),
        success => {
          val dto = success.body.get
          formState.set(EditMilestoneForm(id = dto.id, name = dto.name, description = dto.description))
        }
      )
    )
    .unsafeRunAndForget()

  spinnerService
    .runWithSpinner(milestoneDateService.findByMilestoneId(milestoneId))
    .map(
      _.fold(
        error => println(error),
        success => milestoneDatesVar.set(success.body.get.map(new EditMilestoneDateComponent(_)))
      )
    )
    .unsafeRunAndForget()

  private def updateMilestoneDates(dateComponents: Seq[EditMilestoneDateComponent]): IO[Unit] =
    dateComponents
      .map(_.formState.now())
      .map { dateForm =>
        val dto = UpdateMilestoneDateReq(
          id = dateForm.id,
          name = dateForm.name,
          description = dateForm.description,
          date = dateForm.dateAsInstant
        )
        milestoneDateService.updateMilestoneDate(dto).toastJustErrorsUnit(_ => ())
      }
      .toList
      .sequence
      .void

  lazy val submitter = Observer[(EditMilestoneForm, Seq[EditMilestoneDateComponent])] {
    case (form, dateComponents) =>
      val dto = UpdateMilestoneReq(id = form.id, name = form.name, description = form.description)
      spinnerService
        .runWithSpinner(milestoneService.updateMilestone(dto))
        .toastMessageDto(_ => "Milestone updated successfully")(_ => updateMilestoneDates(dateComponents))
        .unsafeRunAndForget()
  }

  private val nameInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.name),
    errors = EventStream.empty,
    labelValue = "Milestone name",
    tgInputType = TgInputType.Text
  )

  private val descriptionTextArea = formState.tgTextAreaManualErrorSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.description),
    errors = EventStream.empty,
    labelValue = "Milestone description"
  )

  val element = div(
    justifyCenterFlex,
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText("Edit a milestone", TgCard.CardHeaderSize.H3),
        body = TgCard.HtmlCardContent(
          div(
            form(
              onSubmit.preventDefault.mapTo(formState.now() -> milestoneDatesVar.now()) --> submitter,
              nameInput.htmlElement.amend(cls("mb-4")),
              descriptionTextArea.htmlElement.amend(cls("mb-4")),
              h5(cls("mb-2 mt-4"), "Milestone dates"),
              children <-- milestoneDatesVar.signal.map(dateComponents =>
                dateComponents.map(
                  _.htmlElement.amend(cls("border border-gray-300 dark:border-gray-600 rounded-lg p-4 mb-4"))
                )
              ),
              TgHtmlButton.update(EventStream.fromValue(false))
            )
          )
        )
      )
      .element
      .amend(heightAndWidthFitContent)
  )

}
