package com.togger.ui.project

import cats.effect.unsafe.IORuntime
import com.togger.components.button.TgHtmlButton
import com.togger.dto._
import com.togger.dto.organization.FindOrganizationResp
import com.togger.routes.Pages.EditProjectPage
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.style.TgCommonStyleJs.{heightAndWidthFitContent, justifyCenterFlex}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.TgTextArea.TgTextAreaSyntax
import com.togger.ui.auth._
import com.togger.ui.components.{DropdownMeta, TgSelectDropdownOption}
import com.togger.ui.components.TgSelectDropdownMultiSelect.TgSelectDropdownMultiSelectSyntax
import com.togger.ui.components.TgSelectDropdownMultiSelectFilter.TgSelectDropdownMultiSelectFilterSyntax
import com.togger.ui.emoji.TgEmojiField.TgEmojiFieldSyntax
import com.togger.ui.localstorage.LocalStorage
import com.togger.ui.organization.OrganizationServiceFe
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.projectlabel.ProjectLabelFeService
import com.togger.ui.toast.Toast
import com.raquo.laminar.api.L._
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
  projectId: UUID,
  val toast: Toast
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
      val membersCall = projectService.findAllMembers(projectId).map { membersResp =>
        membersResp.fold(
          error => println(error),
          success => {
            val dto = success.body.get
            existingMembers.set(dto)
          }
        )
      }
      val orgCall = organizationServiceFe
        .findByAccountId(accountService.get.get.id)
        .map(
          _.fold(
            e => println(e),
            success => organizationsVar.set(success.body.get)
          )
        )

      val projectLabelCall = projectLabelService
        .findAll()
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
        .toastMessageFn("Project updated successfully")(_ => router.route(EditProjectPage(projectId)))
        .unsafeRunAndForget()
    }
  }

  private val nameInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.name),
    errors = EventStream.empty,
    labelValue = "Project name",
    tgInputType = TgInputType.Text
  )

  private val descriptionTextArea = formState.tgTextAreaManualErrorSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.description),
    errors = EventStream.empty,
    labelValue = "Project description"
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
    val options = projectLabels.sortBy(_.name).map(ts =>
      TgSelectDropdownOption(value = ts.id.toString, meta = DropdownMeta(label = ts.name, description = ""), render = () => span(ts.name))
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
              nameInput.htmlElement.amend(cls("mb-4")),
              iconInput.htmlElement.amend(cls("mb-4")),
              descriptionTextArea.htmlElement.amend(cls("mb-4")),
              workDaysSelect.htmlElement.amend(cls("mb-4")),
              child <-- projectLabelsVar.signal.map(projectLabelSelectElement).map(_.amend(cls("mb-4"))),
              findAccountsComponent.element.amend(width.px(500)),
              TgHtmlButton.update(EventStream.fromValue(false))
            )
          )
        )
      )
      .element
      .amend(heightAndWidthFitContent)
  )

}
