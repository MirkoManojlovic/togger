package com.togger.ui.task

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.common.TgJavaTimeSyntax.TgInstantOps
import com.togger.common._
import com.togger.dto.FindTaskRespFull
import com.togger.dto.FindTaskStatusResp
import com.togger.ui.auth.TgInput.tgInputStyle
import com.togger.ui.components._
import com.togger.ui.modal.ModalStyles
import com.togger.ui.slacker.SlackerMissingDays
import com.togger.ui.slacker.SlackerScope
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.toast.Toast

import java.time.DayOfWeek
import java.time.LocalDate

/** Closing tasks from a list (a bulk status edit to Done or Won't do): asks for one end date, then closes the tasks one
  * after the other. A task with work days that have no log entry goes through the slacker first, like closing it on its
  * own page. Render [[element]] once in the screen that uses the flow.
  */
class TaskCloseFlow(
  taskService: TaskFeService,
  spinnerService: SpinnerService,
  tgClock: TgClock,
  tgDateFormater: TgDateFormater,
  toast: Toast,
  createSlacker: (SlackerScope, () => Unit, () => Unit) => HtmlElement
)(implicit ioRuntime: IORuntime) {

  private val currentVar: Var[Option[HtmlElement]] = Var(None)

  val element: HtmlElement = div(child.maybe <-- currentVar.signal)

  private def dismiss(): Unit = currentVar.set(None)

  /** Asks for the end date of `tasks`, then closes them with `status`; `onFinished` runs when the flow ends, whether
    * all were closed or it was cancelled, so the list can be read again.
    */
  def start(
    tasks: List[FindTaskRespFull],
    status: FindTaskStatusResp,
    workDays: Set[DayOfWeek],
    onFinished: () => Unit
  ): Unit =
    if (tasks.nonEmpty) currentVar.set(Some(dialog(tasks, status, workDays, onFinished)))

  private def closeOne(
    task: FindTaskRespFull,
    status: FindTaskStatusResp,
    endDate: LocalDate,
    workDays: Set[DayOfWeek],
    proceed: () => Unit,
    onFinished: () => Unit
  ): Unit = {
    val update = IO.defer(
      taskService.updateTask(
        ShowAllTasksBulkMapping.toUpdateRequest(
          task.copy(taskStatus = status, endDate = Some(endDate)),
          tgClock.currentInstant
        )
      )
    )
    val abort = () => { dismiss(); onFinished() }
    spinnerService
      .runWithSpinner(taskService.findTaskById(task.id))
      .flatMap {
        case Left(error) => IO { toast.error(error); abort() }
        case Right(found) =>
          val missing = SlackerMissingDays.of(
            found.body.get.logEntries.map(_.createdAt.toLocalDate()),
            task.startDate,
            endDate,
            workDays
          )
          val save = spinnerService.runWithSpinner(update).flatMap {
            case Left(error) => IO { toast.error(error); abort() }
            case Right(_)    => IO { dismiss(); proceed() }
          }
          if (missing.isEmpty) save
          else
            IO(
              currentVar.set(
                Some(
                  createSlacker(SlackerScope(task.id, task.projectId, endDate), abort, () => save.unsafeRunAndForget())
                )
              )
            )
      }
      .unsafeRunAndForget()
  }

  private def closeAll(
    tasks: List[FindTaskRespFull],
    status: FindTaskStatusResp,
    endDate: LocalDate,
    workDays: Set[DayOfWeek],
    onFinished: () => Unit
  ): Unit =
    tasks match {
      case Nil => dismiss(); onFinished()
      case task :: rest =>
        closeOne(
          task,
          status,
          endDate,
          workDays,
          () => closeAll(rest, status, endDate, workDays, onFinished),
          onFinished
        )
    }

  private def dialog(
    tasks: List[FindTaskRespFull],
    status: FindTaskStatusResp,
    workDays: Set[DayOfWeek],
    onFinished: () => Unit
  ): HtmlElement = {
    val latestStart = tasks.map(_.startDate).max
    val endDateVar = Var(tgClock.currentDate())
    val pickerOpen = Var(false)
    val errors = endDateVar.signal.map(date => TaskClose.endDateErrors(Some(date), latestStart, workDays))
    val cancel = () => { dismiss(); onFinished() }

    val picker = CalendarPickerPopup(
      initialMonth = endDateVar.now(),
      today = tgClock.currentDate(),
      tgDateFormater = tgDateFormater,
      selected = Some(endDateVar.now()),
      minDate = Some(latestStart),
      isDisabled = day => !workDays.contains(day.getDayOfWeek),
      onPick = day => endDateVar.set(day),
      onClose = () => pickerOpen.set(false)
    ).element

    div(
      cls(ModalStyles.wrapperCls),
      ModalStyles.closeOnBackdropOrEscape(Observer(_ => cancel())),
      div(
        cls(ModalStyles.contentBoxCls),
        cls("w-full max-w-sm"),
        h3(cls(ModalStyles.titleCls), cls("mb-4"), s"Close ${tasks.size} task${if (tasks.size == 1) "" else "s"}"),
        p(cls("mb-4 text-sm text-body"), s"Status: ${status.name}"),
        label(cls("block mb-2.5 text-sm font-medium text-heading"), "End date"),
        div(
          cls("relative"),
          input(
            typ("text"),
            readOnly(true),
            cls(tgInputStyle),
            cls("cursor-pointer"),
            cls("ring-2 ring-blue-500") <-- pickerOpen.signal,
            aria.label("End date"),
            value <-- endDateVar.signal.map(date => s"${DateEmoji.End} ${tgDateFormater.asAmericanDate(date)}"),
            // stopPropagation keeps this click from reaching the popup's own outside-click listener
            onClick.stopPropagation.mapToUnit --> (_ => pickerOpen.update(!_))
          ),
          child.maybe <-- pickerOpen.signal.map(Option.when(_)(picker))
        ),
        children <-- errors.map(_.map(error => p(cls("mt-2 text-sm text-danger"), error))),
        div(
          cls(ModalStyles.footerCls),
          cls("justify-end mt-72"),
          button(
            typ("button"),
            cls(ModalStyles.secondaryButtonCls),
            "Cancel",
            onClick.mapToUnit --> (_ => cancel())
          ),
          button(
            typ("button"),
            cls(ModalStyles.primaryButtonCls),
            cls("disabled:opacity-50 disabled:cursor-not-allowed"),
            disabled <-- errors.map(_.nonEmpty),
            "Close",
            onClick.mapToUnit --> (_ => closeAll(tasks, status, endDateVar.now(), workDays, onFinished))
          )
        )
      )
    )
  }
}
