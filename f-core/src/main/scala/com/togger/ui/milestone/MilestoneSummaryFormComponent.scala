package com.togger.ui.milestone

import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveHtmlElement
import com.togger.common.TgClock
import com.togger.components.button.TgHtmlButton
import com.togger.dto.{FindMilestoneSummaryResp, FindTgAiActionResp}
import com.togger.ui.auth.TgSelect
import com.togger.ui.components.{TgBadge, TgBadgeColor, TgTab, TgTabs}
import com.togger.ui.components.wysiwyg.WYSIWYGTextEditorSplit.WYSIWYGTextEditorSplitSyntax
import com.togger.ui.organization.TgIcons
import com.togger.ui.task.TaskConfigTableSupport.{iconButton, rowButtonMarginCls, rowIconButtonDangerCls}
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro
import org.scalajs.dom.HTMLFormElement

import scala.collection.immutable.ListMap

import java.time.DayOfWeek
import java.util.UUID

/** `id` is empty until the summary is saved. */
case class MilestoneSummaryForm(
  id: Option[UUID] = None,
  summary: String = "",
  aiSummary: String = "",
  aiActionId: String = "",
  aiPrompt: String = "",
  startDate: String = "",
  endDate: String = ""
)

object MilestoneSummaryForm {
  def fromDto(dto: FindMilestoneSummaryResp): MilestoneSummaryForm =
    MilestoneSummaryForm(
      id = Some(dto.id),
      summary = dto.summary,
      startDate = dto.startDate.toString,
      endDate = dto.endDate.toString
    )
}

sealed trait AiSummaryState
object AiSummaryState {
  case object Idle extends AiSummaryState
  case object Streaming extends AiSummaryState
  case object Finished extends AiSummaryState
}

/** The form of one summary, shared by the saved ones and the new one; it saves itself, apart from the milestone. */
class MilestoneSummaryFormComponent(
  initial: MilestoneSummaryForm,
  tgClock: TgClock,
  onSave: MilestoneSummaryFormComponent => Unit,
  onRemove: MilestoneSummaryFormComponent => Unit,
  onAiSummary: MilestoneSummaryFormComponent => Unit,
  aiActions: Option[Signal[Seq[FindTgAiActionResp]]] = None,
  onAiAction: (MilestoneSummaryFormComponent, FindTgAiActionResp) => Unit = (_, _) => ()
) {
  val formState = Var(initial)

  /** Whether the AI is writing the summary into the editor, which can take a while on a local model. */
  val aiSummaryState = Var[AiSummaryState](AiSummaryState.Idle)

  private val aiSummaryRunning: Signal[Boolean] = aiSummaryState.signal.map(_ == AiSummaryState.Streaming)

  private val summaryEditor = formState.wysiwygSplitEditorSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.summary),
    labelValue = "Summary",
    placeholderValue = "Summarize the period",
    minHeightRem = 18,
    minWidthRem = 0
  )

  private val aiSummaryEditor = formState.wysiwygSplitEditorSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.aiSummary),
    labelValue = "Summary",
    placeholderValue = "Summarize the period, or pick an AI action",
    minHeightRem = 18,
    minWidthRem = 0
  )

  private val selectedTabVar = Var(MilestoneSummaryFormComponent.SummaryTabId)

  /** The text of the tab being shown, which is the one that gets saved. */
  def textToSave: String =
    if (aiActions.isDefined && selectedTabVar.now() == MilestoneSummaryFormComponent.AiSummaryTabId) formState.now().aiSummary
    else formState.now().summary

  private val aiActionList = Var(Seq.empty[FindTgAiActionResp])

  private val aiPromptEditor = formState.wysiwygSplitEditorSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.aiPrompt),
    labelValue = "Prompt",
    placeholderValue = "Pick an AI action to edit its prompt",
    minHeightRem = 10,
    minWidthRem = 0
  )

  private def chooseAiAction(id: String): Unit =
    formState.update(
      _.copy(aiActionId = id, aiPrompt = aiActionList.now().find(_.id.toString == id).fold("")(_.prompt))
    )

  private def aiActionSelect(actions: Seq[FindTgAiActionResp]): HtmlElement =
    TgSelect(
      inputSignal = formState.signal.map(_.aiActionId),
      inputStateUpdater = Observer[String](chooseAiAction),
      labelValue = "AI action",
      emptyValue = Some("Select an AI action"),
      valuesAndDisplays = ListMap(actions.map(action => action.id.toString -> action.name): _*)
    ).htmlElement

  // The action as chosen, with the prompt as the user edited it.
  private def runAiAction(): Unit =
    aiActionList.now().find(_.id.toString == formState.now().aiActionId).foreach { action =>
      onAiAction(this, action.copy(prompt = formState.now().aiPrompt))
    }

  private val runAiActionButton: HtmlElement =
    button(
      typ("button"),
      cls(TgHtmlButton.tailwindExtraSmallButton),
      disabled <-- aiSummaryRunning.combineWithFn(formState.signal.map(_.aiActionId.isEmpty))(_ || _),
      child.text <-- aiSummaryRunning.map(running => if (running) "Writing summary..." else "Run AI action"),
      onClick.mapToUnit --> (_ => runAiAction())
    )

  private def aiActionsPanel(actions: Signal[Seq[FindTgAiActionResp]]): HtmlElement =
    div(
      cls("flex min-w-0 flex-col gap-2"),
      actions --> aiActionList.writer,
      child <-- actions.map(aiActionSelect),
      child.maybe <-- actions.map(list =>
        Option.when(list.isEmpty)(div(cls("text-sm text-body"), "No AI action is bound to the milestone summary."))
      ),
      aiPromptEditor.htmlElement,
      div(cls("flex justify-end"), runAiActionButton)
    )

  private def summaryTabs(actions: Signal[Seq[FindTgAiActionResp]]): HtmlElement =
    new TgTabs(
      Seq(
        TgTab(MilestoneSummaryFormComponent.SummaryTabId, "Summary", summaryEditor.htmlElement),
        TgTab(
          MilestoneSummaryFormComponent.AiSummaryTabId,
          "AI Summary",
          div(
            cls("grid grid-cols-1 gap-4 md:grid-cols-3"),
            aiSummaryEditor.htmlElement.amend(cls("md:col-span-2 min-w-0")),
            aiActionsPanel(actions)
          )
        )
      ),
      selectedTabVar
    ).element

  private val everyDay = DayOfWeek.values.toSet

  // The dates are the start and "due" of the badges, which are labelled Start and End here.
  private val dateBadges = new MilestoneDateBadges(
    dates = formState.signal.map(form => form.startDate -> form.endDate),
    currentDates = () => formState.now().startDate -> formState.now().endDate,
    currentWorkDays = () => everyDay,
    onDates = (start, end) => formState.update(_.copy(startDate = start.toString, endDate = end.toString)),
    tgClock = tgClock,
    dueLabel = "End"
  )

  private def presetBadge(preset: MilestoneSummaryPreset): HtmlElement = {
    val range = MilestoneSummaryPresets.range(preset, tgClock.currentDate())
    button(
      typ("button"),
      cls("rounded-base cursor-pointer hover:ring-2 hover:ring-blue-300"),
      title(s"${range._1} - ${range._2}"),
      TgBadge(text = preset.label, color = TgBadgeColor.Brand).element,
      onClick.mapToUnit --> (_ => formState.update(_.copy(startDate = range._1.toString, endDate = range._2.toString)))
    )
  }

  private val isSaved: Signal[Boolean] = formState.signal.map(_.id.isDefined).distinct

  private val deleteButton: HtmlElement =
    iconButton(
      "Delete summary",
      TgIcons.trashIcon(className = "w-4 h-4"),
      rowIconButtonDangerCls,
      rowButtonMarginCls,
      onClick.mapToUnit --> (_ => onRemove(this))
    )

  private val aiSummaryButton: HtmlElement =
    button(
      typ("button"),
      cls(TgHtmlButton.tailwindExtraSmallButton),
      disabled <-- aiSummaryRunning,
      child.text <-- aiSummaryRunning.map(running => if (running) "Writing summary..." else "AI Summary"),
      onClick.mapToUnit --> (_ => onAiSummary(this))
    )

  private val aiSummaryIndicator: HtmlElement =
    span(
      cls("mr-auto flex items-center gap-1.5 text-sm text-gray-500 dark:text-gray-400"),
      child.maybe <-- aiSummaryState.signal.map {
        case AiSummaryState.Idle => None
        case AiSummaryState.Streaming =>
          Some(
            span(
              cls("flex items-center gap-1.5"),
              span(cls("inline-block h-2 w-2 animate-pulse rounded-full bg-blue-500")),
              "The AI is writing the summary..."
            )
          )
        case AiSummaryState.Finished =>
          Some(span(cls("flex items-center gap-1.5 text-green-600 dark:text-green-400"), "✓ The AI finished the summary"))
      }
    )

  val htmlElement: ReactiveHtmlElement[HTMLFormElement] =
    form(
      onSubmit.preventDefault.mapToUnit --> (_ => onSave(this)),
      div(
        cls("mb-2 flex flex-wrap items-center gap-1.5"),
        dateBadges.element,
        div(cls("ml-auto"), child.maybe <-- isSaved.map(Option.when(_)(deleteButton)))
      ),
      div(cls("mb-4 flex flex-wrap items-center gap-1.5"), MilestoneSummaryPreset.all.map(presetBadge)),
      (aiActions match {
        case Some(actions) => summaryTabs(actions)
        case None          => summaryEditor.htmlElement
      }).amend(cls("mb-4")),
      div(
        cls("flex items-center justify-end gap-2"),
        aiSummaryIndicator,
        child.maybe <-- isSaved.map(saved => Option.when(!saved && aiActions.isEmpty)(aiSummaryButton)),
        child <-- isSaved.map(saved =>
          if (saved) TgHtmlButton.updateSmall(EventStream.fromValue(false))
          else TgHtmlButton.createSmall(EventStream.fromValue(false))
        )
      )
    )

  override def toString = s"MilestoneSummaryFormComponent(${formState.now()})"
}

object MilestoneSummaryFormComponent {
  val SummaryTabId = "summary"
  val AiSummaryTabId = "ai-summary"
}
