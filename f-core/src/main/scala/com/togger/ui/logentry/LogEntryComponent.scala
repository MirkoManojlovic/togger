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
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.TgSelect.TgSelectSyntax
import com.togger.ui.auth._
import com.togger.ui.components.LogEntryStatusBadgeColor.LogEntryStatusBadgeColorOps
import com.togger.ui.components.wysiwyg.WYSIWYGTextEditorSplit.WYSIWYGTextEditorSplitSyntax
import com.togger.ui.components.wysiwyg.WYSIWYGTriggerContext
import com.togger.ui.components.{CalendarPickerPopup, TgBadge, TgBadgeColor}
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.{TaskFeService, TgDateFormater}
import com.togger.ui.toast.Toast
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro

import java.time.{Instant, LocalDate, ZoneOffset}
import java.util.UUID
import scala.util.Try

case class LogEntryForm(
  logEntryId: Option[UUID] = None,
  taskId: UUID,
  status: String = "",
  textExternal: String = "",
  textInternal: String = "",
  date: String = ""
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
      status = LogEntryStatus.InProgress.statusName,
      date = tgClock.currentDate().toString
    )

  def fromDto(dto: LogEntryDto) =
    LogEntryForm(
      logEntryId = Some(dto.id),
      taskId = dto.taskId,
      status = dto.status.value.toString,
      textExternal = dto.textExternal,
      textInternal = dto.textInternal,
      date = dto.updatedAt.toLocalDate().toString
    )
}

class LogEntryComponent(
  logEntryForm: LogEntryForm,
  taskService: TaskFeService,
  spinnerService: SpinnerService,
  accountService: AccountService,
  tgClock: TgClock,
  router: TgRouter[FrontendPage],
  val toast: Toast,
  tgMarkdown: TgMarkdown
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
                      cls("flex"),
                      justifyContent.spaceBetween, // TODO: fix this
                      div(
                        cls("flex items-center gap-2"),
                        TgBadge(
                          text = tgDateFormater.asAmericanDateWithDayAbrv(date),
                          bordered = true,
                          color = TgBadgeColor.Gray
                        ).element,
                        TgBadge(text = status.statusName, color = status.badgeColor, bordered = true).element
                      ),
                      TgHtmlButton
                        .extraSmall("Edit")
                        .amend(
                          onClick.mapToUnit --> { _ =>
                            displayAsEditVar.update(displayAsEdit => !displayAsEdit)
                          }
                        )
                    ),
                    TgCard.CardHeaderSize.H3
                  ),
                  body = TgCard.HtmlCardContent(
                    div(tgMarkdown.toLaminarDiv(state.textExternal))
                  )
                )
                .element
            }
          )
        }
      }
    )
  )

  /** `$` trigger popup: a filterable list of log entry statuses, the same values `statusInputSelect` offers. */
  private def statusTriggerPopup(ctx: WYSIWYGTriggerContext): HtmlElement = {
    val query = ctx.token.query.toLowerCase
    val matches = LogEntryStatus.values.filter(_.statusName.toLowerCase.contains(query))
    div(
      cls("w-56 bg-neutral-primary-medium border border-default-medium rounded-base shadow-lg"),
      ul(
        cls("p-2 text-sm text-body font-medium max-h-72 overflow-y-auto"),
        if (matches.isEmpty) Seq(li(cls("p-2 text-sm text-body"), "No matches"))
        else
          matches.map { status =>
            li(
              button(
                `type`("button"),
                cls(
                  "flex items-center w-full p-2 text-sm font-medium hover:bg-neutral-tertiary-medium rounded-md cursor-pointer"
                ),
                onMouseDown.preventDefault --> Observer.empty,
                onClick.mapToUnit --> { _ =>
                  formState.update(_.copy(status = status.value.toString))
                  ctx.consume()
                },
                TgBadge(text = status.statusName, color = status.badgeColor, bordered = true).element
              )
            )
          }
      )
    )
  }

  /** `@` trigger popup: a month calendar, the same `date` field `dateInput` edits directly. */
  private def dateTriggerPopup(ctx: WYSIWYGTriggerContext): HtmlElement = {
    val currentDate = Try(LocalDate.parse(formState.now().date)).getOrElse(tgClock.currentDate())
    CalendarPickerPopup(
      initialMonth = currentDate,
      today = tgClock.currentDate(),
      tgDateFormater = tgDateFormater,
      selected = Some(currentDate),
      onPick = day => {
        formState.update(_.copy(date = day.toString))
        ctx.consume()
      }
    ).element
  }

  private def asDisplayEl(submitter: Observer[LogEntryForm], buttonLabel: String, addShowToggle: Boolean) = {

    val statusInputSelect = formState.tgSelectManualError(
      inputAndUpdater = selectorAndUpdaterMacro(_.status),
      labelValue = "Status",
      valuesAndDisplays = LogEntryStatus.namesToValuesMapString
    )

    val textExternalEditor = formState.wysiwygSplitEditorSig(
      inputAndUpdater = selectorAndUpdaterMacro(_.textExternal),
      labelValue = "Status update",
      minWidthRem = 40,
      triggers = Map(
        '$' -> statusTriggerPopup _,
        '@' -> dateTriggerPopup _
      )
    )

//    val textInternalTextArea = formState.tgTextAreaManualErrorSig(
//      inputAndUpdater = selectorAndUpdaterMacro(_.textInternal),
//      errors = EventStream.empty,
//      labelValue = "Internal text",
//      isRequired = false,
//      numberOfColumns = 80
//    )

    val dateInput = formState.tgInputManualErrorLPSig(
      inputAndUpdater = selectorAndUpdaterMacro(_.date),
      errors = EventStream.empty,
      labelValue = "Date created",
      tgInputType = TgInputType.Date
    )

    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.html(
          div(
            cls("flex"),
            justifyContent.spaceBetween, // TODO: fix this
            div("Create a daily log entry"),
            if (addShowToggle) {
              TgHtmlButton
                .extraSmall("Show")
                .amend(
                  onClick.mapToUnit --> { _ =>
                    displayAsEditVar.update(displayAsEdit => !displayAsEdit)
                  }
                )
            } else div()
          ),
          TgCard.CardHeaderSize.H3
        ),
        body = TgCard.HtmlCardContent(
          div(
            form(
              onSubmit.preventDefault.mapTo(formState.now()) --> submitter,
              div(
                cls("flex mb-4"),
                columnGap("2rem"),
                div(
                  flexGrow(1),
                  minWidth("0"),
                  textExternalEditor.htmlElement,
                  p(cls("mt-1 text-xs text-gray-500"), "@ date  $ status")
                ),
//              div(
//                cls("flex mb-4"),
//                textInternalTextArea.htmlElement,
//                div(
//                  marginLeft.rem(1),
//                  h3("Preview internal"),
//                  children <-- formState.signal.map(_.textInternal).map(markdownToHtml)
//                )
//              ),
                div(
                  flexShrink(0),
                  width("280px"),
                  statusInputSelect.htmlElement.amend(cls("mb-4")),
                  dateInput.htmlElement.amend(cls("mb-4"))
                )
              ),
              div(TgHtmlButton.primary(buttonLabel))
            )
          )
        )
      )
      .element
      .amend(heightAndWidthFitContent)

  }

}
