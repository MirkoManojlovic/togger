package com.togger.ui.project

import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.components.button.TgHtmlButton
import com.togger.dto._
import com.togger.dto.organization.FindOrganizationResp
import com.togger.routes.Pages.{EditProjectPage, HomePage}
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.style.TgCommonStyleJs.{heightAndWidthFitContent, justifyCenterFlex}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth._
import com.togger.ui.components.TgSelectDropdownMultiSelect.TgSelectDropdownMultiSelectSyntax
import com.togger.ui.components.TgSelectDropdownMultiSelectFilter.TgSelectDropdownMultiSelectFilterSyntax
import com.togger.ui.components.wysiwyg.WYSIWYGTextEditorSplit.WYSIWYGTextEditorSplitSyntax
import com.togger.ui.components.{DropdownMeta, TgSelectDropdownOption}
import com.togger.ui.emoji.TgEmojiField.TgEmojiFieldSyntax
import com.togger.ui.localstorage.LocalStorage
import com.togger.ui.modal.{ModalFactory, PlainModalVariant, PlainTextModalMessage}
import com.togger.ui.navbar.SidebarRefresh
import com.togger.ui.organization.OrganizationServiceFe
import com.togger.ui.projectlabel.ProjectLabelFeService
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.toast.Toast
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro

import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.{Locale, UUID}

case class EditProjectForm(
  projectId: UUID = UUID.randomUUID(),
  name: String = "",
  description: String = "",
  icon: String = "",
  organizationId: UUID = UUID.randomUUID(),
  workDays: Set[String] = Set.empty,
  projectLabelSelected: Set[String] = Set.empty
)

class EditProjectComponent(
  findAccountService: FindAccountService,
  projectService: ProjectService,
  projectLabelService: ProjectLabelFeService,
  organizationServiceFe: OrganizationServiceFe,
  accountService: AccountService,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  localStorage: LocalStorage,
  sidebarRefresh: SidebarRefresh,
  projectId: UUID,
  val toast: Toast,
  modalFactory: ModalFactory
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  val formState = Var(EditProjectForm())
  val organizationsVar = Var(Seq.empty[FindOrganizationResp])
  val existingMembers: Var[Seq[SearchAccountDto]] = Var(Seq.empty)
  val projectLabelsVar = Var(Seq.empty[FindProjectLabelResp])

  val findAccountsComponent = new FindAccountsComponent(
    findAccountService = findAccountService,
    accountService = accountService,
    existingAccountsVar = existingMembers,
    excludeUserAccount = true
  )

  spinnerService
    .runWithSpinner {
      // The member editor lists every member, read page by page (bounded by PaginatedLoader).
      val membersCall = projectService.findAllMembers(projectId).map { membersResp =>
        membersResp.fold(
          error => println(error),
          success => {
            val dto = success.body.get
            existingMembers.set(dto)
          }
        )
      }
      // The organization picker offers every organization of the account, read page by page.
      val orgCall = organizationServiceFe
        .findAllByAccountId(accountService.get.get.id)
        .map(
          _.fold(
            e => println(e),
            success => organizationsVar.set(success.body.get)
          )
        )

      // The label picker offers every project label, read page by page (bounded by PaginatedLoader).
      val projectLabelCall = projectLabelService
        .findEvery()
        .map(_.map(success => projectLabelsVar.set(success.body.get)))

      val projectOrg = projectService
        .findById(projectId)
        .map(
          _.fold(
            e => println(e),
            success => {
              val dto = success.body.get
              formState.set(
                EditProjectForm(
                  projectId = dto.id,
                  name = dto.name,
                  description = dto.description,
                  icon = dto.icon,
                  organizationId = dto.organizationId,
                  workDays = dto.workDays.map(i => DayOfWeek.of(i).name()),
                  projectLabelSelected = dto.projectLabels.map(_.id.toString).toSet
                )
              )
            }
          )
        )

      membersCall.flatMap(_ => orgCall.flatMap(_ => projectLabelCall.flatMap(_ => projectOrg)))
    }
    .unsafeRunAndForget()

  lazy val updateSubmitter = Observer[EditProjectForm] { state =>
    if (state.workDays.isEmpty) {
      toast.error("Select at least one work day")
    } else {
      val request = UpdateProjectReq(
        id = state.projectId,
        organizationId = state.organizationId,
        name = state.name,
        description = state.description,
        icon = state.icon,
        workDays = state.workDays.map(name => DayOfWeek.valueOf(name).getValue),
        membersToAdd = findAccountsComponent.membersToAdd.map(_.id),
        membersToRemove = findAccountsComponent.membersToRemove.map(_.id),
        projectLabelIds = state.projectLabelSelected.map(UUID.fromString).toSeq
      )

      spinnerService
        .runWithSpinner(projectService.updateProject(request))
        .toastMessageFn("Project updated successfully") { _ =>
          sidebarRefresh.request()
          router.route(EditProjectPage(projectId))
        }
        .unsafeRunAndForget()
    }
  }

  private def deleteProject(): Unit =
    modalFactory.plainModal(
      message = PlainTextModalMessage(
        s"Delete project '${formState.now().name}'? All of its data (tasks, log entries, timeboxes, milestones, " +
          "statuses, labels and configurations) is deleted too. This cannot be undone."
      ),
      title = "Delete project",
      variant = PlainModalVariant.Danger,
      confirmButtonMsg = "Yes, delete it",
      cancelButtonMsg = "Cancel",
      confirmationText = Some(formState.now().name)
    ) { close =>
      spinnerService
        .runWithSpinner(projectService.deleteProject(projectId))
        .toastMessageFn("Project deleted successfully") { _ =>
          sidebarRefresh.request()
          router.route(HomePage)
        }
        .unsafeRunAndForget()
      close()
    }

  private val nameInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.name),
    errors = EventStream.empty,
    labelValue = "Project name",
    tgInputType = TgInputType.Text
  )

  private val descriptionEditor = formState.wysiwygSplitEditorSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.description),
    labelValue = "Project description",
    placeholderValue = "Describe the project"
  )

  private val iconInput = formState.tgEmojiPicker(
    inputAndUpdater = selectorAndUpdaterMacro(_.icon),
    errors = EventStream.empty,
    labelValue = "Project icon",
    localStorage = localStorage
  )

  private val workDaysOptions: Seq[TgSelectDropdownOption] =
    DayOfWeek
      .values()
      .toSeq
      .map(day =>
        TgSelectDropdownOption(
          value = day.name(),
          meta = DropdownMeta(label = day.getDisplayName(TextStyle.FULL, Locale.ENGLISH), description = ""),
          render = () => span(day.getDisplayName(TextStyle.FULL, Locale.ENGLISH))
        )
      )

  private val workDaysSelect = formState.tgSelectDropdownMultSelectManualError(
    inputAndUpdater = selectorAndUpdaterMacro(_.workDays),
    labelValue = "Work days",
    options = workDaysOptions
  )

  private def projectLabelSelectElement(projectLabels: Seq[FindProjectLabelResp]): HtmlElement = {
    val options = projectLabels
      .sortBy(_.name)
      .map(ts =>
        TgSelectDropdownOption(
          value = ts.id.toString,
          meta = DropdownMeta(label = ts.name, description = ""),
          render = () => span(ts.name)
        )
      )
    formState
      .tgSelectDropdownMultiSelectFilterManualError(
        inputAndUpdater = selectorAndUpdaterMacro(_.projectLabelSelected),
        labelValue = "Select project labels",
        options = options
      )
      .htmlElement
  }

  val element = div(
    justifyCenterFlex,
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText("Edit a project", TgCard.CardHeaderSize.H3),
        body = TgCard.HtmlCardContent(
          div(
            form(
              onSubmit.preventDefault.mapTo(formState.now()) --> updateSubmitter,
              nameInput.htmlElement.amend(cls("mb-4 max-w-[60ch]")),
              iconInput.htmlElement.amend(cls("mb-4")),
              descriptionEditor.htmlElement.amend(cls("mb-4")),
              workDaysSelect.htmlElement.amend(cls("mb-4")),
              child <-- projectLabelsVar.signal.map(projectLabelSelectElement).map(_.amend(cls("mb-4"))),
//              findAccountsComponent.element.amend(width.px(500)),
              div(
                cls("flex justify-between"),
                button(
                  typ("button"),
                  cls(
                    "text-white bg-danger box-border border border-transparent hover:bg-danger-strong " +
                      "focus:ring-4 focus:ring-danger-medium shadow-xs font-medium leading-5 rounded-base " +
                      "text-xs px-3 py-1.5 focus:outline-none"
                  ),
                  "Delete project",
                  onClick.mapToUnit --> (_ => deleteProject())
                ),
                TgHtmlButton.updateSmall(EventStream.fromValue(false))
              )
            )
          )
        )
      )
      .element
      .amend(heightAndWidthFitContent)
  )

}
