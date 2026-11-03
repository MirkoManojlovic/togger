package com.togger.ui.milestone

import com.raquo.laminar.api.L._
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
  workDays: Signal[Set[DayOfWeek]]
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

  private val startDateInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.startDate),
    errors = EventStream.empty,
    labelValue = "Step start date",
    tgInputType = TgInputType.Date
  )

  private val dueDateInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.dueDate),
    errors = EventStream.empty,
    labelValue = "Step due date",
    tgInputType = TgInputType.Date
  )

  private val descriptionEditor = formState.wysiwygSplitEditorSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.description),
    labelValue = "Step description",
    minHeightRem = 8,
    minWidthRem = 0
  )

  val htmlElement: ReactiveHtmlElement[HTMLDivElement] =
    div(
      nameInput.htmlElement.amend(cls("mb-3 max-w-[60ch]")),
      div(
        cls("mb-3 flex flex-wrap gap-4"),
        startDateInput.htmlElement,
        dueDateInput.htmlElement
      ),
      descriptionEditor.htmlElement,
      children <-- errors.map(_.map(message => p(cls("mt-2 text-sm text-red-600 dark:text-red-500"), message)))
    )
}
