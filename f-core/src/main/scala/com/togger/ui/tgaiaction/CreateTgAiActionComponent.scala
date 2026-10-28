package com.togger.ui.tgaiaction

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.common.TgJsonSuccess
import com.togger.components.button.TgHtmlButton
import com.togger.dto.{FindTgPromptTemplateResp, TgAiEndpointDto, TgAiModelDto}
import com.togger.style.TgCommonStyleJs.{heightAndWidthFitContent, justifyCenterFlex}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgCard
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.toast.Toast
import com.togger.ui.tgprompttemplate.TgPromptTemplateFeService

class CreateTgAiActionComponent(
  tgAiActionService: TgAiActionFeService,
  tgPromptTemplateService: TgPromptTemplateFeService,
  spinnerService: SpinnerService,
  val toast: Toast
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  val formState = Var(TgAiActionForm())

  private val fieldsVar = Var(Option.empty[TgAiActionFormFields])

  private def load(): IO[Unit] =
    for {
      templates <- tgPromptTemplateService.findAllTemplates
      endpoints <- tgAiActionService.listEndpoints
      models <- tgAiActionService.listModels
    } yield (templates, endpoints) match {
      case (Right(templatesSuccess), Right(endpointsSuccess)) =>
        fieldsVar.set(
          Some(
            new TgAiActionFormFields(
              formState,
              templatesSuccess.body.getOrElse(Seq.empty[FindTgPromptTemplateResp]),
              TgAiActionFormSupport.modelsOrEmpty(models),
              endpointsSuccess.body.getOrElse(List.empty[TgAiEndpointDto])
            )
          )
        )
      case (Left(failure), _) => println(failure)
      case (_, Left(failure)) => println(failure)
    }

  spinnerService.runWithSpinner(load()).unsafeRunAndForget()

  lazy val submitter = Observer[TgAiActionForm] { form =>
    spinnerService
      .runWithSpinner(tgAiActionService.create(TgAiActionFormSupport.toCreateReq(form)))
      .toastMessageJustMessage("AI action created successfully")
      .unsafeRunAndForget()
  }

  val element = div(
    justifyCenterFlex,
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText("Create an AI action", TgCard.CardHeaderSize.H3),
        body = TgCard.HtmlCardContent(
          div(
            child <-- fieldsVar.signal.map {
              case None => div()
              case Some(fields) =>
                form(
                  onSubmit.preventDefault.mapTo(formState.now()) --> submitter,
                  fields.elements,
                  div(cls("flex justify-end"), TgHtmlButton.createSmall(EventStream.fromValue(false)))
                )
            }
          )
        )
      )
      .element
      .amend(heightAndWidthFitContent)
  )
}
