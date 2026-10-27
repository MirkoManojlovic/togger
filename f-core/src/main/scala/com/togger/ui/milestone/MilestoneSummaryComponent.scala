package com.togger.ui.milestone

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.common.TgClock
import com.togger.dto.{CreateMilestoneSummaryReq, UpdateMilestoneSummaryReq}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgCard
import com.togger.ui.modal.{ModalFactory, PlainModalVariant, PlainTextModalMessage}
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.toast.Toast

import java.time.LocalDate
import java.util.UUID

class MilestoneSummaryComponent(
  milestoneSummaryService: MilestoneSummaryFeService,
  spinnerService: SpinnerService,
  tgClock: TgClock,
  milestoneId: UUID,
  val toast: Toast,
  modalFactory: ModalFactory
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  // The summaries are saved on their own, apart from the milestone: the new one by its Create button, the previous
  // ones (newest period first) by their Update button.
  val previousSummariesVar = Var(Seq.empty[MilestoneSummaryFormComponent])
  val newSummaryVar = Var(newSummaryForm(MilestoneSummaryForm()))

  private def newSummaryForm(form: MilestoneSummaryForm): MilestoneSummaryFormComponent =
    new MilestoneSummaryFormComponent(form, tgClock, saveSummary, removeSummary)

  private def removeSummary(component: MilestoneSummaryFormComponent): Unit =
    component.formState.now().id.foreach { id =>
      modalFactory.plainModal(
        message = PlainTextModalMessage("Delete this summary and all of its versions?"),
        title = "Delete summary",
        variant = PlainModalVariant.Danger,
        confirmButtonMsg = "Yes, delete it",
        cancelButtonMsg = "Cancel"
      ) { close =>
        spinnerService
          .runWithSpinner(milestoneSummaryService.deleteMilestoneSummary(id))
          .toastBackendMessageCodeBlock {
            previousSummariesVar.update(_.filterNot(_ eq component))
          }
          .unsafeRunAndForget()
        close()
      }
    }

  private def saveSummary(component: MilestoneSummaryFormComponent): Unit = {
    val form = component.formState.now()
    if (form.startDate.isEmpty || form.endDate.isEmpty) toast.error("Choose the start and end date of the summary")
    else {
      val startDate = LocalDate.parse(form.startDate)
      val endDate = LocalDate.parse(form.endDate)
      val saved = form.id match {
        case Some(id) =>
          spinnerService
            .runWithSpinner(
              milestoneSummaryService.updateMilestoneSummary(UpdateMilestoneSummaryReq(id, form.summary, startDate, endDate))
            )
            .toastMessageDto(_ => "Summary updated successfully")(_ => IO.unit)
        case None =>
          spinnerService
            .runWithSpinner(
              milestoneSummaryService.createMilestoneSummary(
                CreateMilestoneSummaryReq(milestoneId, form.summary, startDate, endDate)
              )
            )
            .toastMessageDto(_ => "Summary created successfully")(created =>
              IO {
                previousSummariesVar.update(newSummaryForm(MilestoneSummaryForm.fromDto(created)) +: _)
                newSummaryVar.set(newSummaryForm(MilestoneSummaryForm()))
              }
            )
      }
      saved.unsafeRunAndForget()
    }
  }

  spinnerService
    .runWithSpinner(milestoneSummaryService.findAllByMilestoneId(milestoneId))
    .map(
      _.fold(
        error => println(error),
        success =>
          previousSummariesVar.set(
            success.body.get
              .sortBy(_.startDate.toEpochDay)(Ordering[Long].reverse)
              .map(dto => newSummaryForm(MilestoneSummaryForm.fromDto(dto)))
          )
      )
    )
    .unsafeRunAndForget()

  private lazy val newSummaryCard: HtmlElement =
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText("New summary", TgCard.CardHeaderSize.H3),
        body = TgCard.HtmlCardContent(div(child <-- newSummaryVar.signal.map(_.htmlElement)))
      )
      .element

  private def previousSummaryRow(component: MilestoneSummaryFormComponent): HtmlElement =
    component.htmlElement.amend(cls("border border-gray-300 dark:border-gray-600 rounded-lg p-4 mb-4"))

  private lazy val previousSummariesCard: HtmlElement =
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText("Previous summaries", TgCard.CardHeaderSize.H4),
        body = TgCard.HtmlCardContent(
          div(
            children <-- previousSummariesVar.signal.map(_.map(previousSummaryRow)),
            child.maybe <-- previousSummariesVar.signal.map(summaries =>
              Option.when(summaries.isEmpty)(div(cls("text-sm text-body"), "No previous summaries yet."))
            )
          )
        )
      )
      .element

  val element: HtmlElement =
    div(cls("flex w-full min-w-0 flex-col gap-8"), newSummaryCard, previousSummariesCard)
}
