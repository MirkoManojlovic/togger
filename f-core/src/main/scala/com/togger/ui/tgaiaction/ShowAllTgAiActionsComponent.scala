package com.togger.ui.tgaiaction

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.dto.{FindTgAiActionResp, TgAiEndpointDto}
import com.togger.routes.Pages.{CreateTgAiActionPage, EditTgAiActionPage, ShowAllTgAiActionsPage, TgAiActionDefaultsPage}
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

class ShowAllTgAiActionsComponent(
  tgAiActionService: TgAiActionFeService,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  tgDateFormater: TgDateFormater,
  modalFactory: ModalFactory,
  params: ListParams,
  val toast: Toast
)(implicit
  ioRuntime: IORuntime
) extends ToastMessages {

  val actionsVar = Var(Seq.empty[FindTgAiActionResp])

  private val endpointsVar = Var(Seq.empty[TgAiEndpointDto])

  private val paginationVar = Var(Option.empty[PaginationState])

  private def showList(newParams: ListParams): Unit =
    router.route(ShowAllTgAiActionsPage(newParams))

  private def loadEndpoints(): IO[Unit] =
    tgAiActionService.listEndpoints.map {
      case Left(failure)  => println(failure)
      case Right(success) => endpointsVar.set(success.body.getOrElse(List.empty))
    }

  /** Reads the page the URL asks for. A page past the end steps back to the last page. */
  private def loadPage(): IO[Unit] =
    tgAiActionService.findAll(params.pageRequest).map {
      case Left(failure) => println(failure)
      case Right(success) =>
        val response = success.body.get
        if (response.items.isEmpty && response.totalNumberOfPages > 0 && params.page > response.totalNumberOfPages)
          showList(params.atPage(response.totalNumberOfPages.toInt, params.size))
        else {
          actionsVar.set(response.items)
          paginationVar.set(Some(PaginationState.fromResponse(response)))
        }
    }

  spinnerService.runWithSpinner(loadEndpoints().flatMap(_ => loadPage())).unsafeRunAndForget()

  private def deleteAction(dto: FindTgAiActionResp): Unit =
    modalFactory.plainModal(
      message = PlainTextModalMessage(s"Delete the AI action '${dto.name}'?"),
      title = "Delete AI action",
      variant = PlainModalVariant.Danger,
      confirmButtonMsg = "Yes, delete it",
      cancelButtonMsg = "Cancel"
    ) { close =>
      spinnerService
        .runWithSpinner(tgAiActionService.deleteById(dto.id))
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
              div("AI actions"),
              div(
                cls("flex"),
                button(
                  `type` := "button",
                  cls("mr-4 text-sm font-medium text-fg-brand hover:underline"),
                  "Default actions per project",
                  onClick.mapToUnit --> (_ => router.route(TgAiActionDefaultsPage))
                ),
                iconButton(
                  "Create AI action",
                  TgIcons.plusIcon(className = "w-4 h-4"),
                  headerIconButtonCls,
                  "mr-2",
                  router.navigateTo(CreateTgAiActionPage)
                )
              )
            ),
            TgCard.CardHeaderSize.H3
          ),
          body = TgCard.HtmlCardContent(
            div(
              child <-- actionsVar.signal.combineWith(endpointsVar.signal).map { case (dtos, endpoints) =>
                div(
                  cls("relative overflow-x-auto bg-neutral-primary-soft shadow-xs rounded-base border border-default"),
                  table(
                    cls("w-full text-sm text-left rtl:text-right text-body"),
                    thead(
                      cls("text-sm text-body bg-neutral-secondary-soft border-b rounded-base border-default"),
                      tr(createTh("name"), createTh("model"), createTh("endpoints"), createTh("last updated"), createTh("Action"))
                    ),
                    tbody(
                      dtos.map { dto =>
                        tr(
                          cls("odd:bg-neutral-primary even:bg-neutral-secondary-soft border-b border-default"),
                          createTd(dto.name),
                          createTd(dto.model),
                          createTd(TgAiActionFormSupport.endpointNames(dto.endpointIds, endpoints)),
                          createTd(tgDateFormater.yearMonthDayTimeFormat(dto.updatedAt)),
                          createTd(
                            div(
                              iconButton(
                                "Edit",
                                TgIcons.editIcon(className = "w-4 h-4"),
                                rowIconButtonCls,
                                rowButtonMarginCls,
                                onClick.mapToUnit --> (_ => router.route(EditTgAiActionPage(dto.id)))
                              ),
                              iconButton(
                                "Delete",
                                TgIcons.trashIcon(className = "w-4 h-4"),
                                rowIconButtonDangerCls,
                                rowButtonMarginCls,
                                onClick.mapToUnit --> (_ => deleteAction(dto))
                              )
                            )
                          )
                        )
                      }
                    )
                  )
                )
              },
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
