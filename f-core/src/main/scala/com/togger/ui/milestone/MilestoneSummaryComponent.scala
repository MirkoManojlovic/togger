package com.togger.ui.milestone

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.common.{TgClock, WorkDayCalendar}
import com.togger.components.button.TgHtmlButton
import com.togger.dto.{
  CreateMilestoneSummaryReq,
  FindMilestoneResp,
  FindTaskRespFull,
  UpdateMilestoneSummaryReq
}
import com.togger.markdown.TgMarkdown
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgCard
import com.togger.ui.modal.{ModalFactory, PlainModalVariant, PlainTextModalMessage}
import com.togger.ui.components.{TgTab, TgTabs}
import com.togger.ui.organization.TgIcons
import com.togger.ui.project.{ProjectService, TaskListItem}
import com.togger.ui.task.TaskConfigTableSupport.{iconButton, rowButtonMarginCls, rowIconButtonCls}
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.{TaskFeService, TaskPreviewComponent, TgDateFormater}
import com.togger.ui.timebox.StagedGrouping
import com.togger.ui.toast.Toast

import java.time.{DayOfWeek, LocalDate, ZoneOffset}
import java.util.UUID

/** The summaries of a milestone on the left, and a preview of each of its tasks on the right to write them from. */
class MilestoneSummaryComponent(
  milestoneSummaryService: MilestoneSummaryFeService,
  milestoneService: MilestoneFeService,
  taskService: TaskFeService,
  projectService: ProjectService,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  tgDateFormater: TgDateFormater,
  tgMarkdown: TgMarkdown,
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

  private val milestoneVar = Var(Option.empty[FindMilestoneResp])
  private val tasksVar = Var(Seq.empty[FindTaskRespFull])
  private val workDaysVar = Var(WorkDayCalendar.DefaultWorkDays)

  private def loadTasks(milestone: FindMilestoneResp): IO[Unit] =
    taskService
      .findAllByMilestone(milestone.projectId, milestoneId)
      .map(_.fold(error => println(error), success => tasksVar.set(success.body.get)))

  private def loadWorkDays(milestone: FindMilestoneResp): IO[Unit] =
    projectService
      .findById(milestone.projectId)
      .map(
        _.fold(
          error => println(error),
          success => Some(success.body.get.workDays.map(DayOfWeek.of)).filter(_.nonEmpty).foreach(workDaysVar.set)
        )
      )

  spinnerService
    .runWithSpinner(milestoneService.findMilestoneById(milestoneId))
    .flatMap(
      _.fold(
        error => IO(println(error)),
        success => {
          val milestone = success.body.get
          milestoneVar.set(Some(milestone))
          loadWorkDays(milestone) >> loadTasks(milestone)
        }
      )
    )
    .unsafeRunAndForget()

  private val previewedTaskVar = Var(Option.empty[(FindTaskRespFull, Option[FindMilestoneResp])])

  private def previewTask(task: FindTaskRespFull, milestone: Option[FindMilestoneResp]): Unit =
    previewedTaskVar.set(Some(task -> milestone))

  private lazy val emptyPreviewCard: HtmlElement =
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText("Task preview", TgCard.CardHeaderSize.H4),
        body = TgCard.HtmlCardContent(div(cls("text-sm text-body"), "Press the eye icon of a task to preview it here."))
      )
      .element
      .amend(cls("w-full"))

  private def previewCard(task: FindTaskRespFull, milestone: Option[FindMilestoneResp]): HtmlElement =
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.html(
          div(
            cls("flex items-center justify-between"),
            div(cls("mr-2"), "Task preview"),
            iconButton(
              "Close preview",
              TgIcons.closeIcon(className = "w-4 h-4"),
              rowIconButtonCls,
              rowButtonMarginCls,
              onClick.mapToUnit --> (_ => previewedTaskVar.set(None))
            )
          )
        ),
        body = TgCard.HtmlCardContent(
          new TaskPreviewComponent(
            task,
            workDaysVar.now(),
            router,
            tgDateFormater,
            tgMarkdown,
            tgClock,
            showName = true,
            milestone.toSeq,
            descriptionOnTop = true
          ).element
        )
      )
      .element
      .amend(cls("w-full"))

  private def taskList(tasks: Seq[FindTaskRespFull], milestone: Option[FindMilestoneResp]): HtmlElement = {
    val taskItem = new TaskListItem(
      router,
      tgDateFormater,
      tgClock,
      milestones = Seq.empty,
      workDaysVar.now(),
      showAssignee = true,
      previewTask(_, milestone)
    )
    ul(tasks.sortBy(_.name).map(taskItem(_)))
  }

  private val selectedStatusVar = Var("")

  // One tab per task status, so a status can be reviewed on its own. The selected tab survives the tasks being
  // reloaded or limited.
  private def statusTabs(
    tasks: Seq[FindTaskRespFull],
    milestone: Option[FindMilestoneResp],
    emptyMessage: String
  ): HtmlElement =
    if (tasks.isEmpty) div(cls("text-sm text-body"), emptyMessage)
    else {
      val tabs = StagedGrouping.ByStatus.group(tasks, Seq.empty).map { group =>
        TgTab(
          id = group.tasks.head.taskStatus.id.toString,
          label = group.title,
          content = taskList(group.tasks, milestone),
          badge = Some(Val(group.tasks.size))
        )
      }
      new TgTabs(tabs, selectedStatusVar).element
    }

  // Whether only the tasks with log entries in the period of the new summary are shown.
  private val summaryLimitVar = Var(true)

  private val newSummaryPeriod: Signal[(String, String)] =
    newSummaryVar.signal.flatMapSwitch(_.formState.signal.map(form => form.startDate -> form.endDate)).distinct

  private def limitButton(text: String, limited: Boolean): HtmlElement =
    button(
      typ("button"),
      cls <-- summaryLimitVar.signal.map(current =>
        if (current == limited) TgHtmlButton.tailwindSmallButton else TgHtmlButton.tailwindExtraSmallButton
      ),
      text,
      onClick.mapToUnit --> (_ => summaryLimitVar.set(limited))
    )

  private val tasksHeader: HtmlElement =
    div(
      cls("flex items-center justify-between"),
      div(cls("mr-2"), "Tasks"),
      div(cls("flex gap-2"), limitButton("Summary limit", limited = true), limitButton("Everything", limited = false))
    )

  private lazy val tasksCard: HtmlElement =
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.html(tasksHeader),
        body = TgCard.HtmlCardContent(
          div(
            child <-- tasksVar.signal
              .combineWith(milestoneVar.signal, workDaysVar.signal, summaryLimitVar.signal, newSummaryPeriod)
              .map { case (tasks, milestone, _, limited, (startDate, endDate)) =>
                if (limited && startDate.nonEmpty && endDate.nonEmpty)
                  statusTabs(
                    MilestoneSummaryComponent
                      .withLogEntriesBetween(tasks, LocalDate.parse(startDate), LocalDate.parse(endDate)),
                    milestone,
                    "No task has log entries in the period of the summary."
                  )
                else statusTabs(tasks, milestone, "No tasks in this milestone.")
              }
          )
        )
      )
      .element

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
    div(
      cls("w-full"),
      tasksCard.amend(cls("w-full mb-4")),
      div(
        cls("flex w-full flex-col items-start gap-4 mb-4 lg:flex-row"),
        div(
          cls("flex w-full min-w-0 flex-col gap-8 lg:w-[40rem] lg:shrink-0"),
          newSummaryCard,
          previousSummariesCard
        ),
        div(
          cls("w-full min-w-0 lg:flex-1"),
          child <-- previewedTaskVar.signal.map {
            case Some((task, milestone)) => previewCard(task, milestone)
            case None                    => emptyPreviewCard
          }
        )
      )
    )
}

object MilestoneSummaryComponent {

  /** The tasks with a log entry created from `startDate` to `endDate`, both included. */
  def withLogEntriesBetween(tasks: Seq[FindTaskRespFull], startDate: LocalDate, endDate: LocalDate): Seq[FindTaskRespFull] =
    tasks.filter(_.logEntries.exists { entry =>
      val day = entry.createdAt.atOffset(ZoneOffset.UTC).toLocalDate
      !day.isBefore(startDate) && !day.isAfter(endDate)
    })
}
