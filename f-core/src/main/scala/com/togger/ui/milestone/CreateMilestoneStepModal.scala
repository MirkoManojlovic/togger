package com.togger.ui.milestone

import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.common.TgClock
import com.togger.components.button.TgHtmlButton
import com.togger.dto.CreateMilestoneStepReq
import com.togger.toast.ToastMessages
import com.togger.ui.modal.ModalStyles
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.toast.Toast

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

/** The form that adds a step to a milestone, in a modal: after the last stage, or with `parallel` in parallel with it.
  * The dates start out as the dates of the milestone. A new modal is built for every opening, so its state starts
  * fresh.
  */
class CreateMilestoneStepModal(
  milestoneStepService: MilestoneStepFeService,
  spinnerService: SpinnerService,
  milestoneId: UUID,
  milestoneStart: LocalDate,
  milestoneDue: LocalDate,
  workDays: Set[DayOfWeek],
  tgClock: TgClock,
  parallel: Boolean,
  onClose: () => Unit,
  onCreated: () => Unit,
  val toast: Toast
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  private val step = new CreateMilestoneStepComponent(
    MilestoneStepDraft(),
    Val(milestoneStart.toString -> milestoneDue.toString),
    Val(workDays),
    tgClock
  )
  step.setAutomaticDates(milestoneStart.toString, milestoneDue.toString)

  private val submitter = Observer[MilestoneStepDraft] { draft =>
    MilestoneStepsDraft.toRequests(List(draft)).headOption.foreach { request =>
      val dto = CreateMilestoneStepReq(
        milestoneId = milestoneId,
        name = request.name,
        description = request.description,
        startDate = request.startDate,
        dueDate = request.dueDate,
        parallel = parallel
      )
      spinnerService
        .runWithSpinner(milestoneStepService.createMilestoneStep(dto))
        .toastJustErrorsUnit { _ =>
          toast.success("Milestone step created successfully")
          onCreated()
          onClose()
        }
        .unsafeRunAndForget()
    }
  }

  val element: HtmlElement =
    div(
      cls(ModalStyles.wrapperCls),
      ModalStyles.closeOnBackdropOrEscape(Observer(_ => onClose())),
      div(
        cls(ModalStyles.contentBoxCls),
        cls("w-full max-w-2xl max-h-[90vh] overflow-y-auto"),
        h3(
          cls(ModalStyles.titleCls),
          cls("mb-4"),
          if (parallel) "Add a step in parallel with the last stage" else "Add a step after the last stage"
        ),
        form(
          onSubmit.preventDefault.mapTo(step.formState.now()) --> submitter,
          step.htmlElement.amend(cls("mb-4")),
          div(
            cls(ModalStyles.footerCls),
            cls("justify-end"),
            button(
              typ("button"),
              cls(ModalStyles.secondaryButtonCls),
              "Cancel",
              onClick.mapToUnit --> (_ => onClose())
            ),
            TgHtmlButton.createSmall(step.errors.map(_.nonEmpty).changes)
          )
        )
      )
    )
}
