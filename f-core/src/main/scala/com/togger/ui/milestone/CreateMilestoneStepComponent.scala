package com.togger.ui.milestone

import com.raquo.laminar.api.L._
import com.togger.common.TgClock
import com.raquo.laminar.nodes.ReactiveHtmlElement
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.TgInputType
import com.togger.ui.components.wysiwyg.WYSIWYGTextEditorSplit.WYSIWYGTextEditorSplitSyntax
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro
import org.scalajs.dom.HTMLDivElement

import java.time.DayOfWeek

/** The form of one step of the milestone that is being created. The dates follow the milestone until they are edited.
  */
class CreateMilestoneStepComponent(
  initial: MilestoneStepDraft,
  milestoneRange: Signal[(String, String)],
  workDays: Signal[Set[DayOfWeek]],
  tgClock: TgClock
) {

  val formState = Var(initial)

  /** The dates last set by the form itself; different dates in the form were typed by the user. */
  private val automaticDates = Var(initial.startDate -> initial.dueDate)

  def datesEdited: Boolean =
    (formState.now().startDate -> formState.now().dueDate) != automaticDates.now()

  def setAutomaticDates(startDate: String, dueDate: String): Unit = {
    automaticDates.set(startDate -> dueDate)
    formState.update(_.copy(startDate = startDate, dueDate = dueDate))
  }

  val errors: Signal[List[String]] =
    formState.signal.combineWith(milestoneRange, workDays).map { case (draft, (start, due), days) =>
      MilestoneStepsDraft.errors(draft, start, due, days)
    }

  private val nameInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.name),
    errors = EventStream.empty,
    labelValue = "Step name",
    tgInputType = TgInputType.Text
  )

  private val workDaysNow = Var(Set.empty[DayOfWeek])

  private val dateBadges = new MilestoneDateBadges(
    dates = formState.signal.map(draft => draft.startDate -> draft.dueDate),
    currentDates = () => formState.now().startDate -> formState.now().dueDate,
    currentWorkDays = () => workDaysNow.now(),
    onDates = (start, due) => formState.update(_.copy(startDate = start.toString, dueDate = due.toString)),
    tgClock = tgClock
  )

  private val descriptionEditor = formState.wysiwygSplitEditorSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.description),
    labelValue = "Step description",
    minHeightRem = 26,
    minWidthRem = 0
  )

  val htmlElement: ReactiveHtmlElement[HTMLDivElement] =
    div(
      nameInput.htmlElement.amend(cls("mb-3 max-w-[60ch]")),
      div(
        cls("mb-3 flex flex-col gap-3"),
        dateBadges.element.amend(workDays --> workDaysNow),
        MilestoneSummaryRangeCalendar.element(
          formState.signal.map(draft => draft.startDate -> draft.dueDate).distinct,
          workDays,
          (start, due) => formState.update(_.copy(startDate = start.toString, dueDate = due.toString))
        )
      ),
      descriptionEditor.htmlElement,
      children <-- errors.map(_.map(message => p(cls("mt-2 text-sm text-red-600 dark:text-red-500"), message)))
    )
}
