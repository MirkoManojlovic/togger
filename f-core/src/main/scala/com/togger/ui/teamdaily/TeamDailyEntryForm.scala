package com.togger.ui.teamdaily

import com.raquo.laminar.api.L._
import com.togger.logentry.LogEntryStatus
import com.togger.ui.components.LogEntryStatusBadgeColor.LogEntryStatusBadgeColorOps
import com.togger.ui.components.wysiwyg.WYSIWYGTextEditorSplit.WYSIWYGTextEditorSplitSyntax
import com.togger.ui.components.wysiwyg.WYSIWYGTriggerEvent
import com.togger.ui.auth.TgCard
import com.togger.ui.components._
import com.togger.ui.logentry.{ActivePopup, LogEntryForm}
import com.togger.ui.task.TgDateFormater
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro
import org.scalajs.dom

import java.time.LocalDate
import java.util.UUID

/** The log entry form of [[com.togger.ui.logentry.LogEntryComponent]] for one day of the team daily. The day is chosen
  * by the stepper, so its badge is only shown and `@` does nothing; the status is picked with `$` or its badge.
  */
class TeamDailyEntryForm(
  taskId: UUID,
  date: LocalDate,
  initial: TeamDailyEntryDraft,
  tgDateFormater: TgDateFormater,
  onChange: TeamDailyEntryDraft => Unit
) {

  private val formState = Var(
    LogEntryForm(
      taskId = taskId,
      status = initial.status.value.toString,
      textExternal = initial.textExternal,
      date = date.toString
    )
  )

  private val activePopupVar: Var[Option[ActivePopup]] = Var(None)
  private val highlightedVar: Var[Int] = Var(0)

  private def matchesOf(query: String): Seq[LogEntryStatus] =
    LogEntryStatus.values.filter(_.statusName.toLowerCase.contains(query.toLowerCase))

  private val triggerObserver = Observer[Option[WYSIWYGTriggerEvent]] {
    case Some(WYSIWYGTriggerEvent('$', query, consume)) =>
      activePopupVar.set(Some(ActivePopup.StatusOpen(query, Some(consume))))
      highlightedVar.set(0)
    case Some(_) => ()
    case None    => activePopupVar.update(current => if (current.exists(_.viaTrigger)) None else current)
  }

  private lazy val textExternalEditor = formState.wysiwygSplitEditorSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.textExternal),
    labelValue = "",
    minWidthRem = 40,
    minHeightRem = 20,
    triggerChars = Set('$'),
    onTriggerChange = triggerObserver,
    onEditorKeyDown = handleEditorKeyDown
  )

  private def choose(status: LogEntryStatus, consume: Option[() => Unit]): Unit = {
    formState.update(_.copy(status = status.value.toString))
    consume.foreach(_())
    activePopupVar.set(None)
    textExternalEditor.focus()
  }

  private def handleEditorKeyDown(event: dom.KeyboardEvent): Unit =
    activePopupVar.now() match {
      case Some(ActivePopup.StatusOpen(query, consume)) =>
        val matches = matchesOf(query)
        event.key match {
          case "ArrowDown" if matches.nonEmpty =>
            event.preventDefault()
            highlightedVar.update(index => (index + 1) % matches.size)
          case "ArrowUp" if matches.nonEmpty =>
            event.preventDefault()
            highlightedVar.update(index => (index - 1 + matches.size) % matches.size)
          case "Enter" if matches.nonEmpty =>
            event.preventDefault()
            choose(matches(highlightedVar.now()), consume)
          case "Escape" if consume.isEmpty =>
            event.preventDefault()
            activePopupVar.set(None)
          case _ => ()
        }
      case _ => ()
    }

  private val dateBadge: HtmlElement =
    TgBadge(
      text = tgDateFormater.asAmericanDateWithDayAbrv(date),
      bordered = true,
      color = TgBadgeColor.Gray
    ).element

  private val statusBadge: HtmlElement =
    div(
      cls("relative"),
      child <-- formState.signal.map(_.status).distinct.map { statusStr =>
        val status = LogEntryStatus.withValue(statusStr.toInt)
        TgBadge(text = status.statusName, color = status.badgeColor, bordered = true).element
          .amend(
            cls("cursor-pointer"),
            cls("ring-2 ring-blue-400") <-- activePopupVar.signal.map(_.exists(_.isInstanceOf[ActivePopup.StatusOpen])),
            onClick.mapToUnit --> { _ =>
              activePopupVar.update {
                case Some(ActivePopup.StatusOpen(_, None)) => None
                case _                                     => Some(ActivePopup.StatusOpen("", None))
              }
              highlightedVar.set(0)
              textExternalEditor.focus()
            }
          )
      },
      child.maybe <-- activePopupVar.signal.map {
        case Some(ActivePopup.StatusOpen(query, consume)) =>
          Some(
            QuickOptionsPopup(
              options = matchesOf(query).map(status =>
                QuickOption(
                  id = status.value.toString,
                  label = status.statusName,
                  description = status.description,
                  isSelected = formState.now().status == status.value.toString,
                  badgeColor = Some(status.badgeColor)
                )
              ),
              isHighlighted = index => highlightedVar.signal.map(_ == index),
              onChoose = option => choose(LogEntryStatus.withValue(option.id.toInt), consume),
              widthCls = "w-96"
            ).element
          )
        case _ => None
      }
    )

  val element: HtmlElement =
    div(
      // each day opens with the cursor in the editor, so Next keeps the user typing; after the editor has mounted
      onMountCallback(_ => dom.window.setTimeout(() => textExternalEditor.focus(), 0): Unit),
      // only what the user changed is reported, not the editor settling on what it was given
      formState.signal.changes
        .map(form => TeamDailyEntryDraft(LogEntryStatus.withValue(form.status.toInt), form.textExternal))
        .filter(draft => draft.status != initial.status || draft.textExternal != initial.textExternal) --> onChange,
      TgCard
        .headerNoFooter(
          header = TgCard.CardHeader.html(div("Status for the day"), TgCard.CardHeaderSize.H3),
          body = TgCard.HtmlCardContent(
            div(
              div(cls("flex items-center gap-2 mb-2"), dateBadge, statusBadge),
              textExternalEditor.htmlElement.amend(cls("mb-1")),
              p(cls("mt-1 text-xs text-body-subtle"), "$ status")
            )
          )
        )
        .element
    )
}
