package com.togger.ui.tgaiaction

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.components.button.TgHtmlButton
import com.togger.dto.{FindTgPromptTemplateResp, TgAiEndpointDto}
import com.togger.routes.Pages.ShowAllTgAiActionsPage
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.style.TgCommonStyleJs.{heightAndWidthFitContent, justifyCenterFlex}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgCard
import com.togger.ui.modal.{ModalFactory, PlainModalVariant, PlainTextModalMessage}
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.toast.Toast
import com.togger.ui.tgprompttemplate.TgPromptTemplateFeService

import java.util.UUID

class EditTgAiActionComponent(
  tgAiActionService: TgAiActionFeService,
  tgPromptTemplateService: TgPromptTemplateFeService,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  modalFactory: ModalFactory,
  tgAiActionId: UUID,
  val toast: Toast
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  val formState = Var(TgAiActionForm(id = tgAiActionId))

  private val fieldsVar = Var(Option.empty[TgAiActionFormFields])

  private def load(): IO[Unit] =
    for {
      action <- tgAiActionService.findById(tgAiActionId)
      templates <- tgPromptTemplateService.findAllTemplates
      endpoints <- tgAiActionService.listEndpoints
    } yield (action, templates, endpoints) match {
      case (Right(actionSuccess), Right(templatesSuccess), Right(endpointsSuccess)) =>
        formState.set(TgAiActionFormSupport.fromDto(actionSuccess.body.get))
        fieldsVar.set(
          Some(
            new TgAiActionFormFields(
              formState,
              templatesSuccess.body.getOrElse(Seq.empty[FindTgPromptTemplateResp]),
              endpointsSuccess.body.getOrElse(List.empty[TgAiEndpointDto])
            )
          )
        )
      case (Left(failure), _, _) => println(failure)
      case (_, Left(failure), _) => println(failure)
      case (_, _, Left(failure)) => println(failure)
    }

  spinnerService.runWithSpinner(load()).unsafeRunAndForget()

  lazy val submitter = Observer[TgAiActionForm] { form =>
    spinnerService
      .runWithSpinner(tgAiActionService.update(TgAiActionFormSupport.toUpdateReq(form)))
      .toastMessageJustMessage("AI action saved successfully")
      .unsafeRunAndForget()
  }

  private def deleteAction(): Unit =
    modalFactory.plainModal(
      message = PlainTextModalMessage(s"Delete the AI action '${formState.now().name}'?"),
      title = "Delete AI action",
      variant = PlainModalVariant.Danger,
      confirmButtonMsg = "Yes, delete it",
      cancelButtonMsg = "Cancel"
    ) { close =>
      spinnerService
        .runWithSpinner(tgAiActionService.deleteById(tgAiActionId))
        .toastBackendMessageCodeBlock {
          router.route(ShowAllTgAiActionsPage())
        }
        .unsafeRunAndForget()
      close()
    }

  val element = div(
    justifyCenterFlex,
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText("Edit an AI action", TgCard.CardHeaderSize.H3),
        body = TgCard.HtmlCardContent(
          div(
            child <-- fieldsVar.signal.map {
              case None => div()
              case Some(fields) =>
                form(
                  onSubmit.preventDefault.mapTo(formState.now()) --> submitter,
                  fields.elements,
                  div(
                    cls("flex justify-between"),
                    button(
                      `type` := "button",
                      cls("text-white bg-danger box-border border border-transparent hover:bg-danger-strong font-medium rounded-base text-sm px-4 py-2.5"),
                      "Delete",
                      onClick.mapToUnit --> (_ => deleteAction())
                    ),
                    TgHtmlButton.createSmall(EventStream.fromValue(false))
                  )
                )
            }
          )
        )
      )
      .element
      .amend(heightAndWidthFitContent)
  )
}
