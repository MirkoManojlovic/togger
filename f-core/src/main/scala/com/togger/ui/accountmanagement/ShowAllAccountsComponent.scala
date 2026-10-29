package com.togger.ui.accountmanagement

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.dto.AccountManagementDto
import com.togger.routes.Pages.{EditAccountPage, ProjectHomePage, ShowAllAccountsPage}
import com.togger.routes.{FrontendPage, ListParams, TgRouter}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgCard
import com.togger.ui.components.TgHtmlTable.{createTd, createTh}
import com.togger.ui.components.TgPaginationComponent
import com.togger.ui.components.pagination.PaginationState
import com.togger.ui.modal.{ModalFactory, PlainModalVariant, PlainTextModalMessage}
import com.togger.ui.organization.TgIcons
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.TaskConfigTableSupport.{iconButton, rowButtonMarginCls, rowIconButtonCls, rowIconButtonDangerCls}
import com.togger.ui.toast.Toast

class ShowAllAccountsComponent(
  accountService: AccountManagementFeService,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  modalFactory: ModalFactory,
  params: ListParams,
  val toast: Toast
)(implicit
  ioRuntime: IORuntime
) extends ToastMessages {

  private val accountsVar = Var(Seq.empty[AccountManagementDto])

  private val paginationVar = Var(Option.empty[PaginationState])

  private def showList(newParams: ListParams): Unit =
    router.route(ShowAllAccountsPage(newParams))

  /** Reads the page the URL asks for. A page past the end steps back to the last page. */
  private def loadPage(): IO[Unit] =
    accountService.findAll(params.pageRequest).map {
      case Left(failure) => toast.error(failure)
      case Right(success) =>
        val response = success.body.get
        if (response.items.isEmpty && response.totalNumberOfPages > 0 && params.page > response.totalNumberOfPages)
          showList(params.atPage(response.totalNumberOfPages.toInt, params.size))
        else {
          accountsVar.set(response.items)
          paginationVar.set(Some(PaginationState.fromResponse(response)))
        }
    }

  spinnerService.runWithSpinner(loadPage()).unsafeRunAndForget()

  private def deleteAccount(dto: AccountManagementDto): Unit =
    modalFactory.plainModal(
      message = PlainTextModalMessage(s"Delete the account of ${dto.firstName} ${dto.lastName} (${dto.email})?"),
      title = "Delete account",
      variant = PlainModalVariant.Danger,
      confirmButtonMsg = "Yes, delete it",
      cancelButtonMsg = "Cancel"
    ) { close =>
      spinnerService
        .runWithSpinner(accountService.deleteById(dto.id))
        .toastBackendMessageCodeBlock {
          spinnerService.runWithSpinner(loadPage()).unsafeRunAndForget()
        }
        .unsafeRunAndForget()
      close()
    }

  private def projectLinks(dto: AccountManagementDto) =
    div(
      cls("flex flex-wrap gap-x-3 gap-y-1"),
      dto.projects.map(project =>
        a(cls("text-fg-brand hover:underline"), project.name, router.navigateTo(ProjectHomePage(project.id)))
      )
    )

  val element = {
    div(
      TgCard
        .headerNoFooter(
          header = TgCard.CardHeader.plainText("Accounts", TgCard.CardHeaderSize.H3),
          body = TgCard.HtmlCardContent(
            div(
              child <-- accountsVar.signal.map { dtos =>
                div(
                  cls("relative overflow-x-auto bg-neutral-primary-soft shadow-xs rounded-base border border-default"),
                  table(
                    cls("w-full text-sm text-left rtl:text-right text-body"),
                    thead(
                      cls("text-sm text-body bg-neutral-secondary-soft border-b rounded-base border-default"),
                      tr(
                        createTh("Id"),
                        createTh("First name"),
                        createTh("Last name"),
                        createTh("Email"),
                        createTh("Account type"),
                        createTh("Projects"),
                        createTh("Action")
                      )
                    ),
                    tbody(
                      dtos.map { dto =>
                        tr(
                          cls("odd:bg-neutral-primary even:bg-neutral-secondary-soft border-b border-default"),
                          createTd(dto.id.toString),
                          createTd(dto.firstName),
                          createTd(dto.lastName),
                          createTd(dto.email),
                          createTd(dto.accountTypeName),
                          createTd(projectLinks(dto)),
                          createTd(
                            div(
                              iconButton(
                                "Edit",
                                TgIcons.editIcon(className = "w-4 h-4"),
                                rowIconButtonCls,
                                rowButtonMarginCls,
                                onClick.mapToUnit --> (_ => router.route(EditAccountPage(dto.id)))
                              ),
                              iconButton(
                                "Delete",
                                TgIcons.trashIcon(className = "w-4 h-4"),
                                rowIconButtonDangerCls,
                                rowButtonMarginCls,
                                onClick.mapToUnit --> (_ => deleteAccount(dto))
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
