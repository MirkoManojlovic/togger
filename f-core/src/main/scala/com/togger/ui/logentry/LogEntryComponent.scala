package com.togger.ui.logentry

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.common.TgClock
import com.togger.common.TgJavaTimeSyntax.TgInstantOps
import com.togger.components.button.TgHtmlButton
import com.togger.dto.LogEntryDto
import com.togger.logentry.LogEntryStatus
import com.togger.markdown.TgMarkdown
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.style.TgCommonStyleJs.heightAndWidthFitContent
import com.togger.toast.ToastMessages
import com.togger.ui.organization.TgIcons
import com.togger.ui.auth._
import com.togger.ui.components.LogEntryStatusBadgeColor.LogEntryStatusBadgeColorOps
import com.togger.ui.components.wysiwyg.WYSIWYGTextEditorSplit.WYSIWYGTextEditorSplitSyntax
import com.togger.ui.components.wysiwyg.WYSIWYGTriggerEvent
import com.togger.ui.components._
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.{TaskFeService, TgDateFormater}
import com.togger.ui.toast.Toast
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro
import org.scalajs.dom

import java.time.{DayOfWeek, Instant, LocalDate, ZoneOffset}
import java.util.UUID
import scala.util.Try

case class LogEntryForm(
  logEntryId: Option[UUID] = None,
  taskId: UUID,
  status: String = "",
  textExternal: String = "",
  textInternal: String = "",
  date: String = "",
  createdByName: String = ""
) {
  lazy val dateAsInstant: Instant = {
    val localDate = LocalDate.parse(date)
    val localDateTime = localDate.atTime(0, 0, 0)
    localDateTime.toInstant(ZoneOffset.UTC)
  }
}

object LogEntryForm {
  def default(taskId: UUID, tgClock: TgClock) =
    LogEntryForm(
      taskId = taskId,
      status = LogEntryStatus.InProgress.value.toString,
      date = tgClock.currentDate().toString
    )

  def fromDto(dto: LogEntryDto) =
    LogEntryForm(
      logEntryId = Some(dto.id),
      taskId = dto.taskId,
      status = dto.status.value.toString,
      textExternal = dto.textExternal,
      textInternal = dto.textInternal,
      date = dto.updatedAt.toLocalDate().toString,
      createdByName = dto.createdByName
    )
}

/** The date/status badge popup currently open above the editor, if any. `consume` is `Some` when a typed trigger
  * (`@`/`+`) opened it — picking a value then also deletes the trigger text from the editor — and `None` when a click
  * on the badge itself opened it, which has no token to remove.
  */
sealed trait ActivePopup {
  def viaTrigger: Boolean
}

object ActivePopup {
  case class DateOpen(consume: Option[() => Unit]) extends ActivePopup {
    override val viaTrigger: Boolean = consume.isDefined
  }

  case class StatusOpen(query: String, consume: Option[() => Unit]) extends ActivePopup {
    override val viaTrigger: Boolean = consume.isDefined
  }
}

/** @param taskStartDate
  *   the earliest date selectable in the date picker — a log entry can't predate its task's start date.
  * @param taskEndDate
  *   the latest date selectable in the date picker, if the task has one — a log entry can't come after its task's
  *   end date.
  * @param takenDates
  *   the status of every other log entry the task already has, keyed by its date (the caller excludes this entry's
  *   own date, if any) — each such date is disabled in the date picker, to keep one entry per day, and ringed in
  *   that status's badge color so it's visibly distinct from a plain disabled (non-work-day) day.
  * @param workDays
  *   the owning project's configured work days — non-work days are disabled in the date picker, same as a taken
  *   date.
  */
class LogEntryComponent(
  logEntryForm: LogEntryForm,
  taskService: TaskFeService,
  spinnerService: SpinnerService,
  accountService: AccountService,
  tgClock: TgClock,
  router: TgRouter[FrontendPage],
  val toast: Toast,
  tgMarkdown: TgMarkdown,
  taskStartDate: LocalDate,
  taskEndDate: Option[LocalDate],
  takenDates: Map[LocalDate, LogEntryStatus],
  workDays: Set[DayOfWeek]
)(implicit
  IORuntime: IORuntime
) extends ToastMessages {
  private val formState = Var(logEntryForm)
  private val displayAsEditVar = Var(false)
  private val tgDateFormater = new TgDateFormater

  lazy val saveSubmitter = Observer[LogEntryForm] { form =>
    val dto = LogEntryDto(
      id = form.logEntryId.getOrElse(UUID.randomUUID()),
      taskId = form.taskId,
      createdBy = accountService.get.get.id,
      createdAt = form.dateAsInstant,
      latestVersion = UUID.randomUUID(),
      textInternal = form.textInternal,
      textExternal = form.textExternal,
      status = LogEntryStatus.withValue(form.status.toInt),
      updatedAt = form.dateAsInstant
    )

    spinnerService
      .runWithSpinner(taskService.saveLogEntry(dto))
      .toastMessageDto(_ => "Log Entry saved!")(_ => IO(router.route(router.currentPageSignal.now())))
      .unsafeRunAndForget()
  }

  lazy val updateSubmitter = Observer[LogEntryForm] { form =>
    val dto = LogEntryDto(
      id = form.logEntryId.getOrElse(UUID.randomUUID()),
      taskId = form.taskId,
      createdBy = accountService.get.get.id,
      createdAt = form.dateAsInstant,
      latestVersion = UUID.randomUUID(),
      textInternal = form.textInternal,
      textExternal = form.textExternal,
      status = LogEntryStatus.withValue(form.status.toInt),
      updatedAt = tgClock.currentInstant
    )

    spinnerService
      .runWithSpinner(taskService.updateLogEntry(dto))
      .toastMessageDto(_ => "Log Entry updated!")(_ => IO(router.route(router.currentPageSignal.now())))
      .unsafeRunAndForget()
  }

  lazy val asEditElement = div(
    asDisplayEl(saveSubmitter, "Create", addShowToggle = false)
  )

  lazy val element =
    if (formState.now().logEntryId.isEmpty) asEditElement
    else asDisplayElement.amend(cls("mt-4"))

  lazy val asDisplayElement = div(
    div(
      child <-- displayAsEditVar.signal.map { displayAsEdit =>
        if (displayAsEdit) {
          asDisplayEl(updateSubmitter, "Update", addShowToggle = true)
        } else {
          div(
            {
              val state = formState.now()
              val status = LogEntryStatus.withValue(state.status.toInt)
              val date = LocalDate.parse(state.date)
              TgCard
                .headerNoFooter(
                  header = TgCard.CardHeader.html(
                    div(
                      cls("flex flex-col"),
                      div(
                        cls("flex justify-end mb-2"),
                        toggleEditButton("Edit", TgIcons.editIcon("w-3 h-3"))
                      ),
                      div(
                        // a badge never breaks its text; those that do not fit the row go on the lines below
                        cls("flex flex-wrap items-center gap-2 [&>*]:whitespace-nowrap"),
                        TgBadge(
                          text = tgDateFormater.asAmericanDateWithDayAbrv(date),
                          bordered = true,
                          color = TgBadgeColor.Gray
                        ).element,
                        TgBadge(text = status.toStringWithEmoji, color = status.badgeColor, bordered = true).element,
                        LogEntryMemberBadge(state.createdByName)
                      )
                    ),
                    TgCard.CardHeaderSize.H3
                  ),
                  body = TgCard.HtmlCardContent(
                    div(tgMarkdown.toLaminarDiv(state.textExternal))
                  )
                )
                .element
                // tighter than the card default, so the badges of the header fit in a narrow column
                .amend(cls("p-3!"))
            }
          )
        }
      }
    )
  )

  /** The small icon button that switches an entry between its display and its edit form. */
  private def toggleEditButton(name: String, icon: SvgElement): HtmlElement =
    button(
      typ("button"),
      cls(
        "text-fg-brand bg-neutral-primary border border-brand hover:bg-brand hover:text-white " +
          "focus:ring-4 focus:ring-brand-subtle rounded-full w-6 h-6 inline-flex items-center justify-center focus:outline-none"
      ),
      aria.label(name),
      title(name),
      icon,
      onClick.mapToUnit --> { _ =>
        displayAsEditVar.update(displayAsEdit => !displayAsEdit)
      }
    )

  private def asDisplayEl(submitter: Observer[LogEntryForm], buttonLabel: String, addShowToggle: Boolean) = {

    // an entry that isn't saved yet is made by whoever is logged in
    val memberName = Option(formState.now().createdByName)
      .filter(_.nonEmpty)
      .orElse(accountService.get.map(LogEntryMemberBadge.nameOf))
      .getOrElse("")
    val activePopupVar: Var[Option[ActivePopup]] = Var(None)
    val highlightedVar: Var[Int] = Var(0)

    def currentStatusMatches: Seq[LogEntryStatus] =
      activePopupVar.now() match {
        case Some(ActivePopup.StatusOpen(query, _)) =>
          LogEntryStatus.values.filter(_.statusName.toLowerCase.contains(query.toLowerCase))
        case _ => Seq.empty
      }

    val triggerObserver = Observer[Option[WYSIWYGTriggerEvent]] {
      case Some(WYSIWYGTriggerEvent('@', _, consume)) =>
        activePopupVar.set(Some(ActivePopup.DateOpen(Some(consume))))
      case Some(WYSIWYGTriggerEvent('$', query, consume)) =>
        activePopupVar.set(Some(ActivePopup.StatusOpen(query, Some(consume))))
        highlightedVar.set(0)
      case Some(_) => ()
      case None    => activePopupVar.update(current => if (current.exists(_.viaTrigger)) None else current)
    }

    lazy val textExternalEditor = formState.wysiwygSplitEditorSig(
      inputAndUpdater = selectorAndUpdaterMacro(_.textExternal),
      labelValue = "",
      minWidthRem = 30,
      minHeightRem = 20,
      triggerChars = Set('@', '$'),
      onTriggerChange = triggerObserver,
      onEditorKeyDown = handleEditorKeyDown
    )

    /** Mirrors `CreateTaskQuick`'s `onInputKeyDown`: the editor keeps focus throughout, so typing a query and
      * navigating it with arrow keys both work from the same keydown stream — unlike a popup that grabs focus for
      * itself, which would stop the editor from ever seeing another keystroke to type with.
      */
    def handleEditorKeyDown(event: dom.KeyboardEvent): Unit =
      activePopupVar.now() match {
        case Some(ActivePopup.StatusOpen(_, consumeOpt)) =>
          val matches = currentStatusMatches
          event.key match {
            case "ArrowDown" if matches.nonEmpty =>
              event.preventDefault()
              highlightedVar.update(index => (index + 1) % matches.size)
            case "ArrowUp" if matches.nonEmpty =>
              event.preventDefault()
              highlightedVar.update(index => (index - 1 + matches.size) % matches.size)
            case "Enter" if matches.nonEmpty =>
              event.preventDefault()
              val chosen = matches(highlightedVar.now())
              formState.update(_.copy(status = chosen.value.toString))
              consumeOpt.foreach(consume => consume())
              activePopupVar.set(None)
              textExternalEditor.focus()
            case "Escape" if consumeOpt.isEmpty =>
              // a click-opened popup has no token for the editor's own Escape handling to dismiss
              event.preventDefault()
              activePopupVar.set(None)
            case _ => ()
          }
        // DateOpen's Escape is handled inside CalendarPickerPopup's own input, via its `onClose`
        case _ => ()
      }

    def dateBadge: HtmlElement =
      div(
        cls("relative"),
        child <-- formState.signal.map(_.date).distinct.map { dateStr =>
          val date = Try(LocalDate.parse(dateStr)).getOrElse(tgClock.currentDate())
          TgBadge(
            text = tgDateFormater.asAmericanDateWithDayAbrv(date),
            bordered = true,
            color = TgBadgeColor.Gray
          ).element
            .amend(
              cls("cursor-pointer"),
              cls("ring-2 ring-blue-400") <-- activePopupVar.signal.map(_.exists(_.isInstanceOf[ActivePopup.DateOpen])),
              // stopPropagation keeps this click from reaching CalendarPickerPopup's own outside-click listener,
              // which mounts (and starts listening) synchronously within this same click when the popup opens
              onClick.stopPropagation.mapToUnit --> { _ =>
                val closing = activePopupVar.now().contains(ActivePopup.DateOpen(None))
                activePopupVar.set(if (closing) None else Some(ActivePopup.DateOpen(None)))
                // opening hands focus to the popup's own input instead (see CalendarPickerPopup's onMountCallback)
                if (closing) textExternalEditor.focus()
              }
            )
        },
        child.maybe <-- activePopupVar.signal.map {
          case Some(ActivePopup.DateOpen(consumeOpt)) =>
            val currentDate = Try(LocalDate.parse(formState.now().date)).getOrElse(tgClock.currentDate())
            Some(
              CalendarPickerPopup(
                initialMonth = currentDate,
                today = tgClock.currentDate(),
                tgDateFormater = tgDateFormater,
                selected = Some(currentDate),
                minDate = Some(taskStartDate),
                maxDate = taskEndDate,
                isDisabled = day => !LogEntryCalendarComponent.isWorkDay(day, workDays) || takenDates.contains(day),
                dayMarkerEmoji = day => takenDates.get(day).map(_.emoji),
                onPick = day => {
                  formState.update(_.copy(date = day.toString))
                  consumeOpt.foreach(consume => consume())
                },
                onClose = () => {
                  activePopupVar.set(None)
                  textExternalEditor.focus()
                }
              ).element
            )
          case _ => None
        }
      )

    def statusBadge: HtmlElement =
      div(
        cls("relative"),
        child <-- formState.signal.map(_.status).distinct.map { statusStr =>
          val status = LogEntryStatus.withValue(statusStr.toInt)
          TgBadge(text = status.toStringWithEmoji, color = status.badgeColor, bordered = true).element
            .amend(
              cls("cursor-pointer"),
              cls("ring-2 ring-blue-400") <-- activePopupVar.signal
                .map(_.exists(_.isInstanceOf[ActivePopup.StatusOpen])),
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
          case Some(ActivePopup.StatusOpen(query, consumeOpt)) =>
            val matches = LogEntryStatus.values.filter(_.statusName.toLowerCase.contains(query.toLowerCase))
            Some(
              QuickOptionsPopup(
                options = matches.map(status =>
                  QuickOption(
                    id = status.value.toString,
                    label = status.toStringWithEmoji,
                    description = status.description,
                    isSelected = formState.now().status == status.value.toString,
                    badgeColor = Some(status.badgeColor)
                  )
                ),
                isHighlighted = index => highlightedVar.signal.map(_ == index),
                onChoose = option => {
                  formState.update(_.copy(status = option.id))
                  consumeOpt.foreach(consume => consume())
                  activePopupVar.set(None)
                  textExternalEditor.focus()
                },
                widthCls = "w-96"
              ).element
            )
          case _ => None
        }
      )

    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.html(
          div(
            cls("flex"),
            justifyContent.spaceBetween, // TODO: fix this
            div("Status for the day"),
            if (addShowToggle) {
              toggleEditButton("Show", TgIcons.eyeIcon("w-3 h-3"))
            } else div()
          ),
          TgCard.CardHeaderSize.H3
        ),
        body = TgCard.HtmlCardContent(
          div(
            form(
              onSubmit.preventDefault.mapTo(formState.now()) --> submitter,
              div(cls("flex items-center gap-2 mb-2"), dateBadge, statusBadge, LogEntryMemberBadge(memberName)),
              textExternalEditor.htmlElement.amend(cls("mb-1")),
              p(cls("mt-1 mb-4 text-xs text-body-subtle"), "@ date  $ status"),
              div(TgHtmlButton.primary(buttonLabel))
            )
          )
        )
      )
      .element
//      .amend(heightAndWidthFitContent)

  }

}
