package com.togger.ui.projectmapping

import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.components.button.TgHtmlButton
import com.togger.dto.projectmapping.ProjectMappingResp
import com.togger.routes.Pages.CreateProjectMappingPage
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgCard
import com.togger.ui.organization.TgIcons
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.toast.Toast

import java.util.UUID

class ShowProjectMappingComponent(
  projectMappingService: ProjectMappingFeService,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  val toast: Toast,
  projectId: UUID
)(implicit
  ioRuntime: IORuntime
) extends ToastMessages {

  private val stateVar = Var(List.empty[ProjectMappingResp])

  spinnerService
    .runWithSpinner(
      projectMappingService
        .findByProjectIdDb(projectId)
        .map(response => response.map(success => stateVar.set(success.body.get.toList)))
    )
    .unsafeRunAndForget()

  private def deleteButton(mapping: ProjectMappingResp) =
    button(
      `type`("button"),
      TgIcons.trashIcon(className = "w-4 h-4 me-1.5 -ms-0.5"),
      "Delete",
      cls(
        "inline-flex items-center text-white bg-red-600 hover:bg-red-700 box-border border border-transparent focus:ring-4 focus:ring-red-300 shadow-xs font-medium leading-5 rounded-base text-sm px-3 py-2 focus:outline-none"
      ),
      onClick.mapToUnit --> (_ =>
        spinnerService
          .runWithSpinner(projectMappingService.deleteByIds(mapping.projectIdDb, mapping.projectIdDump))
          .toastBackendMessageCodeBlock {
            stateVar.update(
              _.filterNot(m => m.projectIdDb == mapping.projectIdDb && m.projectIdDump == mapping.projectIdDump)
            )
          }
          .unsafeRunAndForget()
      )
    )

  private def mappingDetails(mapping: ProjectMappingResp) =
    div(
      cls("relative overflow-x-auto bg-neutral-primary-soft shadow-xs rounded-base border border-default p-4"),
      p(cls("mb-2"), b("Dump project id: "), mapping.projectIdDump.toString),
      p(cls("mb-2"), b("Db project id: "), mapping.projectIdDb.toString),
      p(cls("mb-2"), b("Created at: "), mapping.createdAt.toString),
      p(cls("mb-4"), b("Updated at: "), mapping.updatedAt.toString),
      div(deleteButton(mapping))
    )

  private def emptyState =
    p(cls("mb-4"), "No project mapping configured for this project yet.")

  private def createButton =
    TgHtmlButton
      .extraSmall("Create")
      .amend(
        router.navigateTo(CreateProjectMappingPage(projectId))
      )

  val element = {
    div(
      TgCard
        .headerNoFooter(
          header = TgCard.CardHeader.plainText("Project mapping", TgCard.CardHeaderSize.H3),
          body = TgCard.HtmlCardContent(
            div(
              cls("space-y-4"),
              child <-- stateVar.signal.map(mappings =>
                if (mappings.isEmpty) emptyState else div(cls("space-y-4"), mappings.map(mappingDetails))
              ),
              createButton
            )
          )
        )
        .element
    )
  }

}
