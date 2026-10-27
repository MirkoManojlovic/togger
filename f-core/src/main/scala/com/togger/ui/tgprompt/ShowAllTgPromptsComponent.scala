package com.togger.ui.tgprompt

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.dto.FindTgPromptResp
import com.togger.routes.Pages.{CreateTgPromptPage, EditTgPromptPage, ShowAllTgPromptsPage}
import com.togger.routes.{FrontendPage, ListParams, TgRouter}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgCard
import com.togger.ui.components.TgHtmlTable.{createTd, createTh}
import com.togger.ui.components.TgPaginationComponent
import com.togger.ui.components.pagination.PaginationState
import com.togger.ui.modal.{ModalFactory, PlainModalVariant, PlainTextModalMessage}
import com.togger.ui.organization.TgIcons
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.TaskConfigTableSupport.{
  headerIconButtonCls,
  iconButton,
  rowButtonMarginCls,
  rowIconButtonCls,
  rowIconButtonDangerCls
}
import com.togger.ui.task.TgDateFormater
import com.togger.ui.toast.Toast

class ShowAllTgPromptsComponent(
  tgPromptService: TgPromptFeService,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  tgDateFormater: TgDateFormater,
  modalFactory: ModalFactory,
  params: ListParams,
  val toast: Toast
)(implicit
  ioRuntime: IORuntime
) extends ToastMessages {

  val promptsVar = Var(Seq.empty[FindTgPromptResp])

  private val paginationVar = Var(Option.empty[PaginationState])

  private def showList(newParams: ListParams): Unit =
    router.route(ShowAllTgPromptsPage(newParams))

  /** Reads the page the URL asks for. A page past the end steps back to the last page. */
  private def loadPage(): IO[Unit] =
    tgPromptService.findAll(params.pageRequest).map {
      case Left(failure) => println(failure)
      case Right(success) =>
        val response = success.body.get
        if (response.items.isEmpty && response.totalNumberOfPages > 0 && params.page > response.totalNumberOfPages)
          showList(params.atPage(response.totalNumberOfPages.toInt, params.size))
        else {
          promptsVar.set(response.items)
          paginationVar.set(Some(PaginationState.fromResponse(response)))
        }
    }

  spinnerService.runWithSpinner(loadPage()).unsafeRunAndForget()

  private def deletePrompt(dto: FindTgPromptResp): Unit =
    modalFactory.plainModal(
      message = PlainTextModalMessage(s"Delete prompt '${dto.title}' and all its versions?"),
      title = "Delete prompt",
      variant = PlainModalVariant.Danger,
      confirmButtonMsg = "Yes, delete it",
      cancelButtonMsg = "Cancel"
    ) { close =>
      spinnerService
        .runWithSpinner(tgPromptService.deleteTgPromptById(dto.id))
        .toastBackendMessageCodeBlock {
          spinnerService.runWithSpinner(loadPage()).unsafeRunAndForget()
        }
        .unsafeRunAndForget()
      close()
    }

  val element = {
    div(
      TgCard
        .headerNoFooter(
          header = TgCard.CardHeader.html(
            div(
              cls("flex justify-between"),
              div("Prompts"),
              iconButton(
                "Create Prompt",
                TgIcons.plusIcon(className = "w-4 h-4"),
                headerIconButtonCls,
                "mr-2",
                router.navigateTo(CreateTgPromptPage)
              )
            ),
            TgCard.CardHeaderSize.H3
          ),
          body = TgCard.HtmlCardContent(
            div(
              child <-- promptsVar.signal.map(dtos =>
                div(
                  cls("relative overflow-x-auto bg-neutral-primary-soft shadow-xs rounded-base border border-default"),
                  table(
                    cls("w-full text-sm text-left rtl:text-right text-body"),
                    thead(
                      cls("text-sm text-body bg-neutral-secondary-soft border-b rounded-base border-default"),
                      tr(createTh("title"), createTh("prompt"), createTh("last updated"), createTh("Action"))
                    ),
                    tbody(
                      dtos.map { dto =>
                        tr(
                          cls("odd:bg-neutral-primary even:bg-neutral-secondary-soft border-b border-default"),
                          createTd(dto.title),
                          createTd(TgPromptFormSupport.preview(dto.prompt)),
                          createTd(tgDateFormater.yearMonthDayTimeFormat(dto.updatedAt)),
                          createTd(
                            div(
                              iconButton(
                                "Edit",
                                TgIcons.editIcon(className = "w-4 h-4"),
                                rowIconButtonCls,
                                rowButtonMarginCls,
                                onClick.mapToUnit --> (_ => router.route(EditTgPromptPage(dto.id)))
                              ),
                              iconButton(
                                "Delete",
                                TgIcons.trashIcon(className = "w-4 h-4"),
                                rowIconButtonDangerCls,
                                rowButtonMarginCls,
                                onClick.mapToUnit --> (_ => deletePrompt(dto))
                              )
                            )
                          )
                        )
                      }
                    )
                  )
                )
              ),
              TgPaginationComponent(
                paginationVar.signal,
                (newPage, newSize) => showList(params.atPage(newPage, newSize))
              )
            )
          )
        )
        .element
    )
  }

}
