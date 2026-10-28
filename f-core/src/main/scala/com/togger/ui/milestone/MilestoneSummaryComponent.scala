package com.togger.ui.milestone

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.common.{TgClock, WorkDayCalendar}
import com.togger.components.button.TgHtmlButton
import com.togger.ai.TgAiEndpoints
import com.togger.dto.{
  CreateMilestoneSummaryReq,
  FindTgAiActionResp,
  FindMilestoneResp,
  FindTaskRespFull,
  UpdateMilestoneSummaryReq
}
import com.togger.markdown.TgMarkdown
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgCard
import com.togger.ui.modal.{ModalFactory, PlainModalVariant, PlainTextModalMessage}
import com.togger.ui.components.TaskStatusBadgeColor.TaskStatusBadgeColorOps
import com.togger.ui.components.{TgBadge, TgBadgeColor, TgTab, TgTabs}
import com.togger.ui.organization.TgIcons
import com.togger.ui.project.{ProjectService, TaskListItem}
import com.togger.ui.task.TaskConfigTableSupport.{iconButton, rowButtonMarginCls, rowIconButtonCls}
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.tgaiaction.TgAiActionFeService
import com.togger.ui.task.{TaskFeService, TaskPreviewComponent, TgDateFormater}
import com.togger.task.DefaultTaskStatus
import com.togger.ui.teamdaily.TeamDailyStatusOrder
import com.togger.ui.timebox.{StagedGrouping, StagedGroup}
import com.togger.ui.toast.Toast

import scala.collection.mutable
import java.time.{DayOfWeek, LocalDate, ZoneOffset}
import java.util.UUID

/** The summaries of a milestone on the left, and a preview of each of its tasks on the right to write them from. */
class MilestoneSummaryComponent(
  milestoneSummaryService: MilestoneSummaryFeService,
  tgAiActionService: TgAiActionFeService,
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
  private val workDaysVar = Var(WorkDayCalendar.DefaultWorkDays)
  private val aiActionsVar = Var(Seq.empty[FindTgAiActionResp])
  val newSummaryVar = Var(newSummaryForm(MilestoneSummaryForm()))

  private def savedSummaryForm(form: MilestoneSummaryForm): MilestoneSummaryFormComponent =
    new MilestoneSummaryFormComponent(
      form,
      tgClock,
      saveSummary,
      removeSummary,
      writeSummaryWithAi(_, None),
      workDays = workDaysVar.signal
    )

  private def newSummaryForm(form: MilestoneSummaryForm): MilestoneSummaryFormComponent =
    new MilestoneSummaryFormComponent(
      form,
      tgClock,
      saveSummary,
      removeSummary,
      writeSummaryWithAi(_, None),
      Some(aiActionsVar.signal),
      (component, action) => writeSummaryWithAi(component, Some(action)),
      workDaysVar.signal
    )

  spinnerService
    .runWithSpinner(tgAiActionService.findEvery)
    .map(
      _.fold(
        error => println(error),
        success =>
          aiActionsVar.set(
            success.body.get.filter(_.endpointIds.contains(TgAiEndpoints.MilestoneSummary.value)).sortBy(_.name)
          )
      )
    )
    .unsafeRunAndForget()

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

  // Replaces the summary text of the form with what the AI writes from the log entries of its period, as it writes it.
  private def writeSummaryWithAi(component: MilestoneSummaryFormComponent, action: Option[FindTgAiActionResp]): Unit = {
    val form = component.formState.now()
    if (form.startDate.isEmpty || form.endDate.isEmpty) toast.error("Choose the start and end date of the summary")
    else {
      component.formState.update(_.copy(aiSummary = ""))
      component.aiSummaryState.set(AiSummaryState.Streaming)
      milestoneSummaryService
        .summariseWithAi(
          action.fold(
            MilestoneSummaryAi.request(milestoneId, LocalDate.parse(form.startDate), LocalDate.parse(form.endDate))
          )(MilestoneSummaryAi.request(_, milestoneId, LocalDate.parse(form.startDate), LocalDate.parse(form.endDate)))
        ) {
          case AiSummaryEvent.Chunk(text) => component.formState.update(f => f.copy(aiSummary = f.aiSummary + text))
          case AiSummaryEvent.Done        => component.aiSummaryState.set(AiSummaryState.Finished)
          case AiSummaryEvent.Failed(message) =>
            component.aiSummaryState.set(AiSummaryState.Idle)
            toast.error(message)
        }
        .guarantee(IO(if (component.aiSummaryState.now() == AiSummaryState.Streaming) component.aiSummaryState.set(AiSummaryState.Idle)))
        .unsafeRunAndForget()
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
              milestoneSummaryService.updateMilestoneSummary(UpdateMilestoneSummaryReq(id, component.textToSave, startDate, endDate))
            )
            .toastMessageDto(_ => "Summary updated successfully")(_ => IO(component.editingVar.set(false)))
        case None =>
          spinnerService
            .runWithSpinner(
              milestoneSummaryService.createMilestoneSummary(
                CreateMilestoneSummaryReq(milestoneId, component.textToSave, startDate, endDate)
              )
            )
            .toastMessageDto(_ => "Summary created successfully")(created =>
              IO {
                previousSummariesVar.update(savedSummaryForm(MilestoneSummaryForm.fromDto(created)) +: _)
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
              .map(dto => savedSummaryForm(MilestoneSummaryForm.fromDto(dto)))
          )
      )
    )
    .unsafeRunAndForget()

  private val milestoneVar = Var(Option.empty[FindMilestoneResp])
  private val tasksVar = Var(Seq.empty[FindTaskRespFull])

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

  private def previewCard(
    task: FindTaskRespFull,
    milestone: Option[FindMilestoneResp],
    summaryPeriod: Option[(LocalDate, LocalDate)],
    onClose: () => Unit
  ): HtmlElement =
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
              onClick.mapToUnit --> (_ => onClose())
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
            summaryPeriod
          ).element
        )
      )
      .element
      .amend(cls("w-full"))

  private def taskList(
    tasks: Seq[FindTaskRespFull],
    milestone: Option[FindMilestoneResp],
    onPreview: (FindTaskRespFull, Option[FindMilestoneResp]) => Unit
  ): HtmlElement = {
    val taskItem = new TaskListItem(
      router,
      tgDateFormater,
      tgClock,
      milestones = Seq.empty,
      workDaysVar.now(),
      showAssignee = true,
      onPreview(_, milestone),
      previewOnRowClick = true
    )
    ul(tasks.sortBy(_.name).map(taskItem(_)))
  }

  private val selectedStatusVar = Var("")

  // One tab per task status, so a status can be reviewed on its own. The selected tab survives the tasks being
  // reloaded or limited.
  private def statusTabs(
    tasks: Seq[FindTaskRespFull],
    milestone: Option[FindMilestoneResp],
    emptyMessage: String,
    selectedVar: Var[String],
    onPreview: (FindTaskRespFull, Option[FindMilestoneResp]) => Unit
  ): HtmlElement =
    if (tasks.isEmpty) div(cls("text-sm text-body"), emptyMessage)
    else {
      val tabs = MilestoneSummaryComponent.byStatusOrder(StagedGrouping.ByStatus.group(tasks, Seq.empty)).map { group =>
        TgTab(
          id = group.tasks.head.taskStatus.id.toString,
          label = s"${group.tasks.head.taskStatus.emoji} ${group.title}",
          content = taskList(group.tasks, milestone, onPreview),
          badge = Some(Val(group.tasks.size))
        )
      }
      new TgTabs(tabs, selectedVar).element
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
                    "No task has log entries in the period of the summary.",
                    selectedStatusVar,
                    previewTask
                  )
                else statusTabs(tasks, milestone, "No tasks in this milestone.", selectedStatusVar, previewTask)
              }
          )
        )
      )
      .element

  // The tasks with log entries in the period of the new summary, counted by status in the order of the tabs below.
  private val newSummaryStatusCounts: Signal[Seq[HtmlElement]] =
    tasksVar.signal.combineWithFn(newSummaryPeriod)((tasks, period) => (tasks, period._1, period._2)).map {
      case (tasks, startDate, endDate) =>
      if (startDate.isEmpty || endDate.isEmpty) Seq.empty
      else {
        val inPeriod =
          MilestoneSummaryComponent.withLogEntriesBetween(tasks, LocalDate.parse(startDate), LocalDate.parse(endDate))
        if (inPeriod.isEmpty) Seq(TgBadge("No tasks", TgBadgeColor.Gray, bordered = true).element)
        else
          MilestoneSummaryComponent.byStatusOrder(StagedGrouping.ByStatus.group(inPeriod, Seq.empty)).map { group =>
            val status = group.tasks.head.taskStatus
            TgBadge(s"${status.badgeText} × ${group.tasks.size}", status.badgeColor, bordered = true).element
          }
      }
    }

  private lazy val newSummaryHeader: HtmlElement =
    div(
      cls("flex flex-wrap items-center justify-between gap-2"),
      div(cls("mr-2"), "New summary"),
      div(cls("flex flex-wrap justify-end gap-1.5"), children <-- newSummaryStatusCounts)
    )

  private lazy val newSummaryCard: HtmlElement =
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.html(newSummaryHeader),
        body = TgCard.HtmlCardContent(div(child <-- newSummaryVar.signal.map(_.htmlElement)))
      )
      .element

  private def summaryToggleButton(component: MilestoneSummaryFormComponent, name: String, icon: SvgElement) =
    button(
      typ("button"),
      cls(
        "text-fg-brand bg-neutral-primary border border-brand hover:bg-brand hover:text-white " +
          "focus:ring-4 focus:ring-brand-subtle rounded-full w-6 h-6 inline-flex items-center justify-center focus:outline-none"
      ),
      aria.label(name),
      title(name),
      icon,
      onClick.mapToUnit --> (_ => component.editingVar.update(!_))
    )

  // The rows are kept, so the tab and the previewed task of a summary stay while the list of summaries changes.
  private val previousSummaryRows = mutable.Map.empty[MilestoneSummaryFormComponent, HtmlElement]

  private def previousSummaryRow(component: MilestoneSummaryFormComponent): HtmlElement =
    previousSummaryRows.getOrElseUpdate(component, buildPreviousSummaryRow(component))

  private val summaryPeriodOf: MilestoneSummaryFormComponent => Signal[Option[(LocalDate, LocalDate)]] =
    _.formState.signal
      .map(form => Option.when(form.startDate.nonEmpty && form.endDate.nonEmpty)(LocalDate.parse(form.startDate) -> LocalDate.parse(form.endDate)))
      .distinct

  // The tasks with log entries in the period of the summary; the one pressed is previewed under the list, with the
  // log entries of the period first.
  private def summaryTasksTab(component: MilestoneSummaryFormComponent): HtmlElement = {
    val selectedStatus = Var("")
    val previewVar = Var(Option.empty[(FindTaskRespFull, Option[FindMilestoneResp])])
    div(
      child <-- tasksVar.signal
        .combineWith(milestoneVar.signal, summaryPeriodOf(component))
        .map { case (tasks, milestone, period) =>
          period.fold[HtmlElement](div(cls("text-sm text-body"), "The summary has no period.")) { case (start, end) =>
            statusTabs(
              MilestoneSummaryComponent.withLogEntriesBetween(tasks, start, end),
              milestone,
              "No task has log entries in the period of the summary.",
              selectedStatus,
              (task, taskMilestone) => previewVar.set(Some(task -> taskMilestone))
            )
          }
        },
      child <-- previewVar.signal.combineWith(summaryPeriodOf(component)).map {
        case (Some((task, milestone)), period) =>
          previewCard(task, milestone, period, () => previewVar.set(None)).amend(cls("mt-4"))
        case _ => emptyNode
      }
    )
  }

  private def buildPreviousSummaryRow(component: MilestoneSummaryFormComponent): HtmlElement = {
    val selectedTab = Var("summary")
    div(
      cls("border border-gray-300 dark:border-gray-600 rounded-lg p-4 mb-4"),
      child <-- component.editingVar.signal.map { editing =>
        if (editing)
          div(
            div(cls("flex justify-end mb-2"), summaryToggleButton(component, "Show", TgIcons.eyeIcon("w-3 h-3"))),
            component.htmlElement
          )
        else
          div(
            div(
              cls("mb-2 flex items-start justify-between gap-2"),
              child <-- component.formState.signal.map(form =>
                TgBadge(
                  text = s"${form.startDate} - ${form.endDate}",
                  bordered = true,
                  color = TgBadgeColor.Gray
                ).element
              ),
              summaryToggleButton(component, "Edit", TgIcons.editIcon("w-3 h-3"))
            ),
            div(
              cls("mb-4"),
              MilestoneSummaryRangeCalendar.element(
                component.formState.signal.map(form => form.startDate -> form.endDate).distinct,
                workDaysVar.signal,
                (_, _) => ()
              )
            ),
            new TgTabs(
              Seq(
                TgTab(
                  "summary",
                  "Summary",
                  div(child <-- component.formState.signal.map(form => tgMarkdown.toLaminarDiv(form.summary)))
                ),
                TgTab("tasks", "Tasks", summaryTasksTab(component))
              ),
              selectedTab
            ).element
          )
      }
    )
  }

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
      cls("flex w-full min-w-0 flex-col gap-8 mb-4"),
      newSummaryCard,
      tasksCard,
      div(
        cls("w-full min-w-0"),
        child <-- previewedTaskVar.signal.map {
          case Some((task, milestone)) => previewCard(task, milestone, None, () => previewedTaskVar.set(None))
          case None                    => emptyPreviewCard
        }
      ),
      previousSummariesCard
    )
}

object MilestoneSummaryComponent {

  private val DefaultStatusRank: Map[String, Int] =
    DefaultTaskStatus.values.map(status => TeamDailyStatusOrder.normalise(status.statusName) -> status.i).toMap

  /** The groups in the order of the default statuses, from In progress to Won't do; any other status after them, by
    * name.
    */
  def byStatusOrder(groups: Seq[StagedGroup]): Seq[StagedGroup] =
    groups.sortBy(group =>
      DefaultStatusRank.getOrElse(TeamDailyStatusOrder.normalise(group.title), DefaultStatusRank.size) -> group.title
    )

  /** The tasks with a log entry created from `startDate` to `endDate`, both included. */
  def withLogEntriesBetween(tasks: Seq[FindTaskRespFull], startDate: LocalDate, endDate: LocalDate): Seq[FindTaskRespFull] =
    tasks.filter(_.logEntries.exists { entry =>
      val day = entry.createdAt.atOffset(ZoneOffset.UTC).toLocalDate
      !day.isBefore(startDate) && !day.isAfter(endDate)
    })
}
