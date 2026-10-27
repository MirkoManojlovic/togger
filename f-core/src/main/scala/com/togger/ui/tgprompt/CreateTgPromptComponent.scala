package com.togger.ui.tgprompt

import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.components.button.TgHtmlButton
import com.togger.dto._
import com.togger.style.TgCommonStyleJs.{heightAndWidthFitContent, justifyCenterFlex}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.{TgCard, TgInputType}
import com.togger.ui.components.wysiwyg.WYSIWYGTextEditorSplit.WYSIWYGTextEditorSplitSyntax
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.toast.Toast
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro

class CreateTgPromptComponent(
  tgPromptService: TgPromptFeService,
  spinnerService: SpinnerService,
  val toast: Toast
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  val formState = Var(TgPromptForm())

  lazy val submitter = Observer[TgPromptForm] { form =>
    spinnerService
      .runWithSpinner(tgPromptService.createTgPrompt(CreateTgPromptReq(title = form.title, prompt = form.prompt)))
      .toastMessageJustMessage("Prompt created successfully")
      .unsafeRunAndForget()
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
    placeholderValue = TgPromptFormSupport.promptHint
  )

  val element = div(
    justifyCenterFlex,
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText("Create a prompt", TgCard.CardHeaderSize.H3),
        body = TgCard.HtmlCardContent(
          div(
            form(
              onSubmit.preventDefault.mapTo(formState.now()) --> submitter,
              titleInput.htmlElement.amend(cls("mb-4")),
              promptEditor.htmlElement.amend(cls("mb-4")),
              div(cls("flex justify-end"), TgHtmlButton.createSmall(EventStream.fromValue(false)))
            )
          )
        )
      )
      .element
      .amend(heightAndWidthFitContent)
  )

}
