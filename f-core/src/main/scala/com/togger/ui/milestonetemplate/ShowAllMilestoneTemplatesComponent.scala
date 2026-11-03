package com.togger.ui.milestonetemplate

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.dto.FindMilestoneTemplateResp
import com.togger.routes.FrontendPage
import com.togger.routes.ListParams
import com.togger.routes.Pages.CreateMilestoneTemplatePage
import com.togger.routes.Pages.EditMilestoneTemplatePage
import com.togger.routes.Pages.ShowAllMilestoneTemplatesPage
import com.togger.routes.TgRouter
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgCard
import com.togger.ui.components.TgHtmlTable.createTd
import com.togger.ui.components.TgHtmlTable.createTh
import com.togger.ui.components.TgPaginationComponent
import com.togger.ui.components.pagination.PaginationState
import com.togger.ui.modal.ModalFactory
import com.togger.ui.modal.PlainModalVariant
import com.togger.ui.modal.PlainTextModalMessage
import com.togger.ui.organization.TgIcons
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.TaskConfigTableSupport.headerIconButtonCls
import com.togger.ui.task.TaskConfigTableSupport.iconButton
import com.togger.ui.task.TaskConfigTableSupport.rowButtonMarginCls
import com.togger.ui.task.TaskConfigTableSupport.rowIconButtonCls
import com.togger.ui.task.TaskConfigTableSupport.rowIconButtonDangerCls
import com.togger.ui.task.TgDateFormater
import com.togger.ui.toast.Toast

import java.util.UUID

class ShowAllMilestoneTemplatesComponent(
  milestoneTemplateService: MilestoneTemplateFeService,
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

  val templatesVar = Var(Seq.empty[FindMilestoneTemplateResp])

  private val paginationVar = Var(Option.empty[PaginationState])

  private def showList(newParams: ListParams): Unit =
    router.route(ShowAllMilestoneTemplatesPage(projectId, newParams))

  /** Reads the page the URL asks for. A page past the end steps back to the last page. */
  private def loadPage(): IO[Unit] =
    milestoneTemplateService.findByProjectId(projectId, params.pageRequest).map {
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

  private def deleteTemplate(dto: FindMilestoneTemplateResp): Unit =
    modalFactory.plainModal(
      message = PlainTextModalMessage(s"Delete milestone template '${dto.name}' and all its versions?"),
      title = "Delete milestone template",
      variant = PlainModalVariant.Danger,
      confirmButtonMsg = "Yes, delete it",
      cancelButtonMsg = "Cancel"
    ) { close =>
      spinnerService
        .runWithSpinner(milestoneTemplateService.deleteMilestoneTemplateById(dto.id))
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
              div("Milestone templates"),
              iconButton(
                "Create Milestone Template",
                TgIcons.plusIcon(className = "w-4 h-4"),
                headerIconButtonCls,
                "mr-2",
                router.navigateTo(CreateMilestoneTemplatePage(projectId))
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
                      tr(createTh("name"), createTh("steps"), createTh("last updated"), createTh("Action"))
                    ),
                    tbody(
                      dtos.map { dto =>
                        tr(
                          cls("odd:bg-neutral-primary even:bg-neutral-secondary-soft border-b border-default"),
                          createTd(dto.name),
                          createTd(dto.steps.map(_.name).mkString(" → ")),
                          createTd(tgDateFormater.yearMonthDayTimeFormat(dto.updatedAt)),
                          createTd(
                            div(
                              iconButton(
                                "Edit",
                                TgIcons.editIcon(className = "w-4 h-4"),
                                rowIconButtonCls,
                                rowButtonMarginCls,
                                onClick.mapToUnit -->
                                  (_ => router.route(EditMilestoneTemplatePage(dto.projectId, dto.id)))
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
