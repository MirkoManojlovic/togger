package com.togger.ui.logentry

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.common.TgClock
import com.togger.common.TgJavaTimeSyntax.TgInstantOps
import com.togger.components.button.TgHtmlButton
import com.togger.dto.LogEntryDto
import com.togger.logentry.LogEntryStatus
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.style.TgCommonStyleJs.heightAndWidthFitContent
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.TgSelect.TgSelectSyntax
import com.togger.ui.auth.TgTextArea.TgTextAreaSyntax
import com.togger.ui.auth._
import com.togger.ui.components.TextToHtmlRender
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.TaskFeService
import com.togger.ui.toast.Toast
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro

import java.time.{DayOfWeek, Instant, LocalDate, ZoneOffset}
import java.util.UUID

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

  lazy val dateDay: DayOfWeek = {
    val localDate = LocalDate.parse(date)
    localDate.getDayOfWeek
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
  val toast: Toast
)(implicit
  IORuntime: IORuntime
) extends ToastMessages {
  private val formState = Var(logEntryForm)
  private val displayAsEditVar = Var(false)

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
              TgCard
                .headerNoFooter(
                  header = TgCard.CardHeader.html(
                    div(
                      cls("flex"),
                      justifyContent.spaceBetween, // TODO: fix this
                      div(b("Date: "), s"${state.date} ${state.dateDay.toString.toLowerCase.capitalize.take(3)}"),
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
                    div(
                      p(b("Status: "), LogEntryStatus.withValue(state.status.toInt).toStringWithEmoji, cls("mb-4")),
                      div(p(b(s"Status update")), new TextToHtmlRender().textToHtmlList(state.textExternal), cls("mb-4"))
//                      div(p(b("Text internal")), markdownToHtml(state.textInternal), cls("mb-4"))
                    )
                  )
                )
                .element
            }
          )
        }
      }
    )
  )

  private def asDisplayEl(submitter: Observer[LogEntryForm], buttonLabel: String, addShowToggle: Boolean) = {

    val statusInputSelect = formState.tgSelectManualError(
      inputAndUpdater = selectorAndUpdaterMacro(_.status),
      labelValue = "Status",
      valuesAndDisplays = LogEntryStatus.namesToValuesMapString
    )

    val textExternalTextArea = formState.tgTextAreaManualErrorSig(
      inputAndUpdater = selectorAndUpdaterMacro(_.textExternal),
      errors = EventStream.empty,
      labelValue = "Status update",
      numberOfColumns = 80
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
              statusInputSelect.htmlElement.amend(cls("mb-4")),
              div(
                cls("flex mb-4"),
                textExternalTextArea.htmlElement
//                div(
//                  marginLeft.rem(1),
//                  h3("Preview external"),
//                  children <-- formState.signal.map(_.textExternal).map(markdownToHtml)
//                )
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
              dateInput.htmlElement.amend(cls("mb-4")),
              div(TgHtmlButton.primary(buttonLabel))
            )
          )
        )
      )
      .element
      .amend(heightAndWidthFitContent)

  }

}
