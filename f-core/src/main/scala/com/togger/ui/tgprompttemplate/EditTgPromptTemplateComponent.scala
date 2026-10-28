package com.togger.ui.tgprompttemplate

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.components.button.TgHtmlButton
import com.togger.dto._
import com.togger.markdown.TgMarkdown
import com.togger.style.TgCommonStyleJs.{heightAndWidthFitContent, justifyCenterFlex}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.{TgCard, TgInputType}
import com.togger.ui.components.wysiwyg.WYSIWYGTextEditorSplit.WYSIWYGTextEditorSplitSyntax
import com.togger.ui.modal.{ModalFactory, PlainModalVariant, PlainTextModalMessage}
import com.togger.ui.organization.TgIcons
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.TaskConfigTableSupport.{iconButton, rowButtonMarginCls, rowIconButtonDangerCls}
import com.togger.ui.task.TgDateFormater
import com.togger.ui.toast.Toast
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro

import java.util.UUID

/** A version is never edited: saving the form adds a new version, and a version can only be deleted. */
class EditTgPromptTemplateComponent(
  tgPromptTemplateService: TgPromptTemplateFeService,
  spinnerService: SpinnerService,
  tgMarkdown: TgMarkdown,
  tgDateFormater: TgDateFormater,
  modalFactory: ModalFactory,
  tgPromptTemplateId: UUID,
  val toast: Toast
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  val formState = Var(TgPromptTemplateForm(id = tgPromptTemplateId))

  private val versionsVar = Var(Seq.empty[TgPromptTemplateVersionDto])

  private def loadVersions(): IO[Unit] =
    tgPromptTemplateService.listAllVersions(tgPromptTemplateId).map {
      case Left(failure) => println(failure)
      case Right(success) =>
        versionsVar.set(TgPromptTemplateFormSupport.newestFirst(success.body.getOrElse(Seq.empty)))
    }

  private def loadForm(): IO[Unit] =
    tgPromptTemplateService.findTgPromptTemplateById(tgPromptTemplateId).map {
      case Left(failure) => println(failure)
      case Right(success) =>
        val dto = success.body.get
        formState.set(TgPromptTemplateForm(id = dto.id, title = dto.title, prompt = dto.prompt))
    }

  private def reload(): IO[Unit] = loadForm().flatMap(_ => loadVersions())

  spinnerService.runWithSpinner(reload()).unsafeRunAndForget()

  lazy val submitter = Observer[TgPromptTemplateForm] { form =>
    val dto = NewTgPromptTemplateVersionReq(id = form.id, title = form.title, prompt = form.prompt)
    spinnerService
      .runWithSpinner(tgPromptTemplateService.saveNewVersion(dto).flatTap {
        case Right(_) => loadVersions()
        case Left(_)  => IO.unit
      })
      .toastMessageJustMessage("Prompt template saved as a new version")
      .unsafeRunAndForget()
  }

  private def deleteVersion(version: TgPromptTemplateVersionDto): Unit =
    modalFactory.plainModal(
      message = PlainTextModalMessage(
        s"Delete the version '${version.title}' from ${tgDateFormater.yearMonthDayTimeFormat(version.createdAt)}?"
      ),
      title = "Delete prompt template version",
      variant = PlainModalVariant.Danger,
      confirmButtonMsg = "Yes, delete it",
      cancelButtonMsg = "Cancel"
    ) { close =>
      spinnerService
        .runWithSpinner(tgPromptTemplateService.deleteVersion(version.version))
        .toastBackendMessageCodeBlock {
          spinnerService.runWithSpinner(reload()).unsafeRunAndForget()
        }
        .unsafeRunAndForget()
      close()
    }

  private val titleInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.title),
    errors = EventStream.empty,
    labelValue = "Title",
    tgInputType = TgInputType.Text
  )

  private val promptEditor = formState.wysiwygSplitEditorSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.prompt),
    labelValue = "Prompt",
    placeholderValue = TgPromptTemplateFormSupport.promptHint
  )

  private def versionRow(version: TgPromptTemplateVersionDto, deletable: Boolean): HtmlElement =
    div(
      cls("mb-3 p-3 border border-default rounded-base bg-neutral-primary-soft"),
      div(
        cls("mb-2 flex justify-between items-center text-xs text-body-subtle"),
        span(s"${version.title} - ${tgDateFormater.yearMonthDayTimeFormat(version.createdAt)}"),
        Option.when(deletable)(
          iconButton(
            "Delete version",
            TgIcons.trashIcon(className = "w-4 h-4"),
            rowIconButtonDangerCls,
            rowButtonMarginCls,
            onClick.mapToUnit --> (_ => deleteVersion(version))
          )
        )
      ),
      tgMarkdown.toLaminarDiv(version.prompt)
    )

  private def historyPanel: HtmlElement =
    div(
      cls("mt-6"),
      h4(cls("mb-2 text-sm font-medium text-heading"), "Version history"),
      children <-- versionsVar.signal.map { versions =>
        val deletable = TgPromptTemplateFormSupport.canDeleteVersion(versions)
        versions.map(versionRow(_, deletable))
      }
    )

  val element = div(
    justifyCenterFlex,
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText("Edit prompt template (saves a new version)", TgCard.CardHeaderSize.H3),
        body = TgCard.HtmlCardContent(
          div(
            form(
              onSubmit.preventDefault.mapTo(formState.now()) --> submitter,
              titleInput.htmlElement.amend(cls("mb-4")),
              promptEditor.htmlElement.amend(cls("mb-4")),
              div(cls("flex justify-end"), TgHtmlButton.updateSmall(EventStream.fromValue(false)))
            ),
            historyPanel
          )
        )
      )
      .element
      .amend(heightAndWidthFitContent)
  )

}
