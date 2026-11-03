package com.togger.ui.milestone

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.components.button.TgHtmlButton
import com.togger.dto.CreateMilestoneStepReq
import com.togger.routes.FrontendPage
import com.togger.routes.Pages.ShowMilestoneStepsPage
import com.togger.routes.TgRouter
import com.togger.style.TgCommonStyleJs.heightAndWidthFitContent
import com.togger.style.TgCommonStyleJs.justifyCenterFlex
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgCard
import com.togger.ui.auth.TgCheckbox
import com.togger.ui.project.ProjectService
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.toast.Toast

import java.time.DayOfWeek
import java.util.UUID

/** Adds a step at the end of a milestone, after the last stage or in parallel with it; its dates start out as the dates
  * of the milestone.
  */
class CreateMilestoneStepPageComponent(
  milestoneStepService: MilestoneStepFeService,
  milestoneService: MilestoneFeService,
  projectService: ProjectService,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  projectId: UUID,
  milestoneId: UUID,
  val toast: Toast
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  private val milestoneRangeVar = Var("" -> "")
  private val workDaysVar = Var(Set.empty[DayOfWeek])

  val step = new CreateMilestoneStepComponent(MilestoneStepDraft(), milestoneRangeVar.signal, workDaysVar.signal)

  private val milestoneNameVar = Var("")
  private val parallelVar = Var(false)

  private def load(): IO[Unit] =
    for {
      milestoneResult <- milestoneService.findMilestoneById(milestoneId)
      projectResult <- projectService.findById(projectId)
    } yield {
      projectResult.fold(toast.error, success => workDaysVar.set(success.body.get.workDays.map(DayOfWeek.of)))
      milestoneResult.fold(
        toast.error,
        success => {
          val milestone = success.body.get
          milestoneNameVar.set(milestone.name)
          milestoneRangeVar.set(milestone.startDate.toString -> milestone.dueDate.toString)
          step.setAutomaticDates(milestone.startDate.toString, milestone.dueDate.toString)
        }
      )
    }

  spinnerService.runWithSpinner(load()).unsafeRunAndForget()

  private val submitter = Observer[MilestoneStepDraft] { draft =>
    MilestoneStepsDraft.toRequests(List(draft)).headOption.foreach { request =>
      val dto = CreateMilestoneStepReq(
        milestoneId = milestoneId,
        name = request.name,
        description = request.description,
        startDate = request.startDate,
        dueDate = request.dueDate,
        parallel = parallelVar.now()
      )
      spinnerService
        .runWithSpinner(milestoneStepService.createMilestoneStep(dto))
        .toastJustErrorsUnit { _ =>
          toast.success("Milestone step created successfully")
          router.route(ShowMilestoneStepsPage(projectId, milestoneId))
        }
        .unsafeRunAndForget()
    }
  }

  val element = div(
    justifyCenterFlex,
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.html(
          div(
            child <-- milestoneNameVar.signal.map(name =>
              span(if (name.isEmpty) "Add a step" else s"Add a step to $name")
            )
          ),
          TgCard.CardHeaderSize.H3
        ),
        body = TgCard.HtmlCardContent(
          div(
            form(
              onSubmit.preventDefault.mapTo(step.formState.now()) --> submitter,
              step.htmlElement.amend(cls("mb-4")),
              TgCheckbox
                .checkBox(
                  parallelVar.signal,
                  parallelVar.writer,
                  "Run in parallel with the last stage of the milestone"
                )
                .htmlElement
                .amend(cls("mb-4")),
              div(
                cls("flex justify-end"),
                TgHtmlButton.createSmall(step.errors.map(_.nonEmpty).changes)
              )
            )
          )
        )
      )
      .element
      .amend(heightAndWidthFitContent)
  )
}
