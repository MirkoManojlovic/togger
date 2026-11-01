package com.togger.ui.milestone

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.dto.{FindMilestoneResp, FindMilestoneStepResp, ReorderMilestoneStepsReq, UpdateMilestoneStepReq}
import com.togger.milestone.{DefaultMilestoneStep, MilestoneStatus}
import com.togger.routes.Pages.{CreateMilestoneStepPage, EditMilestonePage, EditMilestoneStepPage}
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgCard
import com.togger.ui.components.TgHtmlTable.{createTd, createTh}
import com.togger.ui.components.{ChartColorBadgeColor, TgBadge, TgBadgeColor}
import com.togger.ui.milestonetemplate.MilestoneTemplateStepsEditing
import com.togger.ui.modal.{ModalFactory, ModalStyles, PlainModalVariant, PlainTextModalMessage}
import com.togger.ui.organization.TgIcons
import com.togger.common.TgClock
import com.togger.ui.project.ProjectService
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.TaskConfigTableSupport.{
  headerIconButtonCls,
  iconButton,
  rowButtonMarginCls,
  rowIconButtonCls,
  rowIconButtonDangerCls
}
import com.togger.ui.task.{TaskFeService, TaskLabelFeService, TaskStatusFeService, TgDateFormater}
import com.togger.ui.timebox.TimeboxStagingStore
import com.togger.ui.toast.Toast

import java.time.{DayOfWeek, LocalDate}
import java.util.UUID

/** The steps of one milestone in order: their state, reordering with a change comment, and the way to close the
  * milestone once all steps are closed.
  */
class ShowMilestoneStepsComponent(
  milestoneStepService: MilestoneStepFeService,
  milestoneService: MilestoneFeService,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  tgDateFormater: TgDateFormater,
  tgClock: TgClock,
  projectService: ProjectService,
  modalFactory: ModalFactory,
  taskService: TaskFeService,
  taskStatusFeService: TaskStatusFeService,
  taskLabelFeService: TaskLabelFeService,
  stagingStore: TimeboxStagingStore,
  isDark: Signal[Boolean],
  projectId: UUID,
  milestoneId: UUID,
  val toast: Toast
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  val milestoneVar = Var(Option.empty[FindMilestoneResp])
  val stepsVar = Var(List.empty[FindMilestoneStepResp])
  private val workDaysVar = Var(Set.empty[DayOfWeek])

  /** The move waiting for its change comment: the index of the step and how far it goes. */
  private val pendingMove = Var(Option.empty[(Int, Int)])
  private val moveComment = Var("")

  /** The dates dragged on the Gantt chart and not saved yet, by step. */
  private val stagedDates = Var(Map.empty[UUID, (LocalDate, LocalDate)])
  private val savingDates = Var(false)
  private val saveComment = Var("")

  private def load(): IO[Unit] =
    for {
      milestoneResult <- milestoneService.findMilestoneById(milestoneId)
      stepsResult <- milestoneStepService.findByMilestoneId(milestoneId)
      projectResult <- projectService.findById(projectId)
    } yield {
      projectResult.foreach(project => workDaysVar.set(project.body.get.workDays.map(DayOfWeek.of)))
      milestoneResult.fold(toast.error, success => milestoneVar.set(success.body))
      stepsResult.fold(toast.error, success => stepsVar.set(success.body.getOrElse(Nil)))
    }

  spinnerService.runWithSpinner(load()).unsafeRunAndForget()

  private def openStepCount: Int = MilestoneStepClose.openSteps(stepsVar.now(), (_: FindMilestoneStepResp).status).size

  private def requestMove(index: Int, delta: Int): Unit = {
    val target = index + delta
    // the default steps stay at the top: they do not move and nothing moves above them
    val steps = stepsVar.now()
    def movable(i: Int) = steps.lift(i).exists(step => !DefaultMilestoneStep.isDefaultName(step.name))
    if (movable(index) && movable(target)) {
      moveComment.set("")
      pendingMove.set(Some(index -> delta))
    }
  }

  private def confirmMove(): Unit =
    pendingMove.now().foreach { case (index, delta) =>
      val order = MilestoneTemplateStepsEditing.move(stepsVar.now().map(_.id), index, delta)
      pendingMove.set(None)
      spinnerService
        .runWithSpinner(
          milestoneStepService.reorderMilestoneSteps(ReorderMilestoneStepsReq(milestoneId, order, moveComment.now()))
        )
        .toastJustErrorsUnit(steps => stepsVar.set(steps))
        .unsafeRunAndForget()
    }

  private def deleteStep(step: FindMilestoneStepResp): Unit =
    modalFactory.plainModal(
      message = PlainTextModalMessage(s"Delete step '${step.name}' and all its versions?"),
      title = "Delete milestone step",
      variant = PlainModalVariant.Danger,
      confirmButtonMsg = "Yes, delete it",
      cancelButtonMsg = "Cancel"
    ) { close =>
      spinnerService
        .runWithSpinner(milestoneStepService.deleteMilestoneStepById(step.id))
        .toastBackendMessageCodeBlock {
          spinnerService.runWithSpinner(load()).unsafeRunAndForget()
        }
        .unsafeRunAndForget()
      close()
    }

  private def statusBadge(step: FindMilestoneStepResp): HtmlElement =
    TgBadge(text = step.status.toStringWithEmoji, color = ChartColorBadgeColor.of(step.status), bordered = true).element

  private val progressBadge: Signal[HtmlElement] =
    stepsVar.signal.map { steps =>
      val closed = steps.count(_.status.isClosing)
      TgBadge(text = s"$closed/${steps.size} steps closed", color = TgBadgeColor.Gray, bordered = true).element
    }

  /** Everything is closed but the milestone is not: offers going to the milestone to close it. */
  private val completionBanner: Signal[Option[HtmlElement]] =
    milestoneVar.signal.combineWith(stepsVar.signal).map { case (milestone, steps) =>
      milestone
        .filter(m => m.status == MilestoneStatus.InProgress || m.status == MilestoneStatus.Paused)
        .filter(_ => MilestoneStepClose.allClosed(steps.map(_.status)))
        .map(_ =>
          div(
            cls("flex items-center justify-between p-4 mb-4 text-sm rounded-base bg-success-soft text-fg-success border border-success-subtle"),
            span("All steps are completed. Close the milestone?"),
            button(
              typ("button"),
              cls(ModalStyles.primaryButtonCls),
              "Go to the milestone",
              onClick.mapToUnit --> (_ => router.route(EditMilestonePage(projectId, milestoneId)))
            )
          )
        )
    }

  /** The milestone was closed while steps were still open: bad planning, which is shown but not prevented. */
  private val closedEarlyBanner: Signal[Option[HtmlElement]] =
    milestoneVar.signal.combineWith(stepsVar.signal).map { case (milestone, steps) =>
      val open = MilestoneStepClose.openSteps(steps, (_: FindMilestoneStepResp).status).size
      milestone
        .filter(_.status == MilestoneStatus.Done)
        .filter(_ => open > 0)
        .map(_ =>
          div(
            cls("p-4 mb-4 text-sm rounded-base bg-warning-soft text-fg-warning border border-warning-subtle"),
            s"This milestone was closed with $open unfinished ${if (open == 1) "step" else "steps"}."
          )
        )
    }

  /** A dialog asking for the change comment that a save needs. */
  private def commentDialog(
    dialogTitle: String,
    hint: String,
    confirmLabel: String,
    comment: Var[String],
    cancel: () => Unit,
    confirm: () => Unit
  ): HtmlElement =
    div(
      cls(ModalStyles.wrapperCls),
      ModalStyles.closeOnBackdropOrEscape(Observer(_ => cancel())),
      div(
        cls(ModalStyles.contentBoxCls),
        cls("w-full max-w-md"),
        h3(cls(ModalStyles.titleCls), cls("mb-4"), dialogTitle),
        label(cls("block mb-2.5 text-sm font-medium text-heading"), "Change comment"),
        textArea(
          cls("w-full p-2.5 text-sm text-heading bg-neutral-secondary-medium border border-default-medium rounded-base"),
          rows(3),
          placeholder(hint),
          controlled(value <-- comment.signal, onInput.mapToValue --> comment)
        ),
        child <-- comment.signal.map(text =>
          MilestoneStepClose.commentErrors(text).headOption.fold[Node](emptyNode)(error =>
            p(cls("mt-2 text-sm text-red-600 dark:text-red-500"), error)
          )
        ),
        div(
          cls(ModalStyles.footerCls),
          cls("justify-end"),
          button(
            typ("button"),
            cls(ModalStyles.secondaryButtonCls),
            "Cancel",
            onClick.mapToUnit --> (_ => cancel())
          ),
          button(
            typ("button"),
            cls(ModalStyles.primaryButtonCls),
            confirmLabel,
            disabled <-- comment.signal.map(MilestoneStepClose.commentErrors(_).nonEmpty),
            onClick.mapToUnit --> (_ => confirm())
          )
        )
      )
    )

  private val moveDialog: HtmlElement =
    commentDialog(
      "Reorder steps",
      "Why are the steps reordered?",
      "Reorder",
      moveComment,
      () => pendingMove.set(None),
      () => confirmMove()
    )

  private val saveDatesDialog: HtmlElement =
    commentDialog(
      "Save step dates",
      "Why are the dates changed?",
      "Save",
      saveComment,
      () => savingDates.set(false),
      () => confirmSaveDates()
    )

  /** The dates are staged only when they differ from the saved ones. */
  private def stageDates(stepId: UUID, start: LocalDate, due: LocalDate): Unit =
    stepsVar.now().find(_.id == stepId).foreach { step =>
      if ((start, due) == ((step.startDate, step.dueDate))) stagedDates.update(_ - stepId)
      else stagedDates.update(_ + (stepId -> (start -> due)))
    }

  private def requestSaveDates(): Unit = {
    saveComment.set("")
    savingDates.set(true)
  }

  private def confirmSaveDates(): Unit = {
    val comment = saveComment.now()
    val requests = stepsVar.now().flatMap { step =>
      stagedDates.now().get(step.id).map { case (start, due) =>
        UpdateMilestoneStepReq(
          step.id,
          step.name,
          step.description,
          step.status,
          start,
          due,
          step.endDate,
          comment
        )
      }
    }
    savingDates.set(false)
    // the steps are saved one after the other, stopping at the first that fails, as the milestone range is checked for each
    val saveAll = requests.foldLeft(IO.pure(true)) { (previous, request) =>
      previous.flatMap { saved =>
        if (!saved) IO.pure(false)
        else milestoneStepService.updateMilestoneStep(request).map(_.fold(failure => { toast.error(failure); false }, _ => true))
      }
    }
    spinnerService
      .runWithSpinner(saveAll.flatMap(saved => IO(if (saved) stagedDates.set(Map.empty)).flatMap(_ => load())))
      .unsafeRunAndForget()
  }

  private val stagedDatesBanner: Signal[Option[HtmlElement]] =
    stagedDates.signal.map { staged =>
      Option.when(staged.nonEmpty)(
        div(
          cls("flex items-center justify-between p-4 mb-4 text-sm rounded-base bg-warning-soft text-fg-warning border border-warning-subtle"),
          span(s"${staged.size} ${if (staged.size == 1) "step has" else "steps have"} unsaved date changes."),
          div(
            cls("flex gap-2"),
            button(
              typ("button"),
              cls(ModalStyles.secondaryButtonCls),
              "Discard",
              onClick.mapToUnit --> (_ => stagedDates.set(Map.empty))
            ),
            button(typ("button"), cls(ModalStyles.primaryButtonCls), "Save", onClick.mapToUnit --> (_ => requestSaveDates()))
          )
        )
      )
    }

  private def row(step: FindMilestoneStepResp, index: Int): HtmlElement =
    tr(
      cls("odd:bg-neutral-primary even:bg-neutral-secondary-soft border-b border-default"),
      createTd((index + 1).toString),
      createTd(step.name),
      createTd(statusBadge(step)),
      createTd(s"${step.tasksDone}/${step.tasksTotal}"),
      createTd(tgDateFormater.asAmericanDate(step.startDate)),
      createTd(tgDateFormater.asAmericanDate(step.dueDate)),
      createTd(step.endDate.map(end => span(tgDateFormater.asAmericanDate(end)))),
      createTd(
        if (DefaultMilestoneStep.isDefaultName(step.name)) emptyNode
        else
          div(
            iconButton(
              "Move up",
              span("↑"),
              rowIconButtonCls,
              rowButtonMarginCls,
              onClick.mapToUnit --> (_ => requestMove(index, -1))
            ),
            iconButton(
              "Move down",
              span("↓"),
              rowIconButtonCls,
              rowButtonMarginCls,
              onClick.mapToUnit --> (_ => requestMove(index, 1))
            ),
            iconButton(
              "Edit",
              TgIcons.editIcon(className = "w-4 h-4"),
              rowIconButtonCls,
              rowButtonMarginCls,
              onClick.mapToUnit --> (_ => router.route(EditMilestoneStepPage(projectId, step.id)))
            ),
            iconButton(
              "Delete",
              TgIcons.trashIcon(className = "w-4 h-4"),
              rowIconButtonDangerCls,
              rowButtonMarginCls,
              onClick.mapToUnit --> (_ => deleteStep(step))
            )
          )
      )
    )

  /** The steps as the chart draws them: the saved ones with the dates dragged so far, and never a default step. */
  private val ganttSteps: Signal[Seq[MilestoneGanttStep]] =
    stepsVar.signal.combineWith(stagedDates.signal).map { case (steps, staged) =>
      steps
        .filterNot(step => DefaultMilestoneStep.isDefaultName(step.name))
        .map { step =>
          val (start, due) = staged.getOrElse(step.id, (step.startDate, step.dueDate))
          MilestoneGanttStep(step.id.toString, step.name, start, due, step.status)
        }
    }

  private val stepsGantt = new MilestoneStepsGanttComponent(
    ganttSteps,
    milestoneVar.signal.map(_.map(milestone => milestone.startDate -> milestone.dueDate)),
    workDaysVar.signal,
    tgDateFormater,
    tgClock,
    onDatesChange = Some((step, start, due) => stageDates(UUID.fromString(step.key), start, due))
  )

  private val stepTasks = new MilestoneStepTasksComponent(
    taskService,
    taskStatusFeService,
    taskLabelFeService,
    milestoneService,
    milestoneStepService,
    projectService,
    tgDateFormater,
    stagingStore,
    spinnerService,
    tgClock,
    isDark,
    projectId,
    milestoneId,
    stepsVar.signal,
    load(),
    toast
  )

  val element: HtmlElement =
    div(
      child.maybe <-- pendingMove.signal.map(_.map(_ => moveDialog)),
      child.maybe <-- savingDates.signal.map(Option.when(_)(saveDatesDialog)),
      child.maybe <-- stagedDatesBanner,
      stepsGantt.element,
      TgCard
        .headerNoFooter(
          header = TgCard.CardHeader.html(
            div(
              cls("flex justify-between"),
              div(
                cls("flex items-center gap-2"),
                child <-- milestoneVar.signal.map(m => span(m.fold("Milestone steps")(milestone => s"Steps of ${milestone.name}"))),
                child <-- progressBadge
              ),
              iconButton(
                "Add step",
                TgIcons.plusIcon(className = "w-4 h-4"),
                headerIconButtonCls,
                "mr-2",
                router.navigateTo(CreateMilestoneStepPage(projectId, milestoneId))
              )
            ),
            TgCard.CardHeaderSize.H3
          ),
          body = TgCard.HtmlCardContent(
            div(
              child.maybe <-- completionBanner,
              child.maybe <-- closedEarlyBanner,
              child <-- stepsVar.signal.map { steps =>
                if (steps.isEmpty) div(cls("text-sm text-body"), "This milestone has no steps yet.")
                else
                  div(
                    cls("relative overflow-x-auto bg-neutral-primary-soft shadow-xs rounded-base border border-default"),
                    table(
                      cls("w-full text-sm text-left rtl:text-right text-body"),
                      thead(
                        cls("text-sm text-body bg-neutral-secondary-soft border-b rounded-base border-default"),
                        tr(
                          createTh("#"),
                          createTh("name"),
                          createTh("status"),
                          createTh("tasks done"),
                          createTh("start"),
                          createTh("due"),
                          createTh("end"),
                          createTh("Action")
                        )
                      ),
                      tbody(steps.zipWithIndex.map { case (step, index) => row(step, index) })
                    )
                  )
              }
            )
          )
        )
        .element,
      stepTasks.element.amend(cls("mt-4"))
    )
}
