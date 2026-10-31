package com.togger.ui.milestonesteptemplate

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.dto.FindMilestoneStepTemplateResp
import com.togger.routes.Pages.{
  CreateMilestoneStepTemplatePage,
  EditMilestoneStepTemplatePage,
  ShowAllMilestoneStepTemplatesPage
}
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

import java.util.UUID

class ShowAllMilestoneStepTemplatesComponent(
  milestoneStepTemplateService: MilestoneStepTemplateFeService,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  tgDateFormater: TgDateFormater,
  modalFactory: ModalFactory,
  projectId: UUID,
  params: ListParams,
  val toast: Toast
)(implicit
  ioRuntime: IORuntime
) extends ToastMessages {

  val templatesVar = Var(Seq.empty[FindMilestoneStepTemplateResp])

  private val paginationVar = Var(Option.empty[PaginationState])

  private def showList(newParams: ListParams): Unit =
    router.route(ShowAllMilestoneStepTemplatesPage(projectId, newParams))

  /** Reads the page the URL asks for. A page past the end steps back to the last page. */
  private def loadPage(): IO[Unit] =
    milestoneStepTemplateService.findByProjectId(projectId, params.pageRequest).map {
      case Left(failure) => toast.error(failure)
      case Right(success) =>
        val response = success.body.get
        if (response.items.isEmpty && response.totalNumberOfPages > 0 && params.page > response.totalNumberOfPages)
          showList(params.atPage(response.totalNumberOfPages.toInt, params.size))
        else {
          templatesVar.set(response.items)
          paginationVar.set(Some(PaginationState.fromResponse(response)))
        }
    }

  spinnerService.runWithSpinner(loadPage()).unsafeRunAndForget()

  private def deleteTemplate(dto: FindMilestoneStepTemplateResp): Unit =
    modalFactory.plainModal(
      message = PlainTextModalMessage(s"Delete milestone step template '${dto.name}' and all its versions?"),
      title = "Delete milestone step template",
      variant = PlainModalVariant.Danger,
      confirmButtonMsg = "Yes, delete it",
      cancelButtonMsg = "Cancel"
    ) { close =>
      spinnerService
        .runWithSpinner(milestoneStepTemplateService.deleteMilestoneStepTemplateById(dto.id))
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
              div("Milestone step templates"),
              iconButton(
                "Create Milestone Step Template",
                TgIcons.plusIcon(className = "w-4 h-4"),
                headerIconButtonCls,
                "mr-2",
                router.navigateTo(CreateMilestoneStepTemplatePage(projectId))
              )
            ),
            TgCard.CardHeaderSize.H3
          ),
          body = TgCard.HtmlCardContent(
            div(
              child <-- templatesVar.signal.map(dtos =>
                div(
                  cls("relative overflow-x-auto bg-neutral-primary-soft shadow-xs rounded-base border border-default"),
                  table(
                    cls("w-full text-sm text-left rtl:text-right text-body"),
                    thead(
                      cls("text-sm text-body bg-neutral-secondary-soft border-b rounded-base border-default"),
                      tr(createTh("name"), createTh("last updated"), createTh("Action"))
                    ),
                    tbody(
                      dtos.map { dto =>
                        tr(
                          cls("odd:bg-neutral-primary even:bg-neutral-secondary-soft border-b border-default"),
                          createTd(dto.name),
                          createTd(tgDateFormater.yearMonthDayTimeFormat(dto.updatedAt)),
                          createTd(
                            div(
                              iconButton(
                                "Edit",
                                TgIcons.editIcon(className = "w-4 h-4"),
                                rowIconButtonCls,
                                rowButtonMarginCls,
                                onClick.mapToUnit -->
                                  (_ => router.route(EditMilestoneStepTemplatePage(dto.projectId, dto.id)))
                              ),
                              iconButton(
                                "Delete",
                                TgIcons.trashIcon(className = "w-4 h-4"),
                                rowIconButtonDangerCls,
                                rowButtonMarginCls,
                                onClick.mapToUnit --> (_ => deleteTemplate(dto))
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
