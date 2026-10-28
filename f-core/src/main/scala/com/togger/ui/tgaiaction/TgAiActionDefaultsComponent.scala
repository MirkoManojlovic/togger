package com.togger.ui.tgaiaction

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.dto.{FindTgAiActionResp, SetTgAiActionDefaultReq, TgAiEndpointDto}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.{AccountService, TgCard, TgSelect}
import com.togger.ui.organization.{OrganizationProjectsLoader, OrganizationWithProjects}
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.toast.Toast

import java.util.UUID
import scala.collection.immutable.ListMap

/** Per project, the action each AI endpoint uses by default for the signed-in account. */
class TgAiActionDefaultsComponent(
  tgAiActionService: TgAiActionFeService,
  organizationProjectsLoader: OrganizationProjectsLoader,
  accountService: AccountService,
  spinnerService: SpinnerService,
  val toast: Toast
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  private val projectsVar = Var(Seq.empty[(UUID, String)])
  private val actionsVar = Var(Seq.empty[FindTgAiActionResp])
  private val endpointsVar = Var(Seq.empty[TgAiEndpointDto])
  private val selectedProjectVar = Var("")

  // endpoint id -> id of the default action as text, absent when there is no default
  private val defaultsVar = Var(Map.empty[Int, String])

  private def load(): IO[Unit] =
    for {
      organizations <- organizationProjectsLoader.loadForAccount(accountService.get.get.id)
      actions <- tgAiActionService.findEvery
      endpoints <- tgAiActionService.listEndpoints
    } yield {
      organizations.foreach(success => projectsVar.set(projectChoices(success.body.getOrElse(Seq.empty))))
      actions.foreach(success => actionsVar.set(success.body.getOrElse(Seq.empty)))
      endpoints.foreach(success => endpointsVar.set(success.body.getOrElse(List.empty)))
    }

  private def projectChoices(organizations: Seq[OrganizationWithProjects]): Seq[(UUID, String)] =
    organizations.flatMap(organization => organization.projects.map(p => p.id -> s"${organization.name} / ${p.name}"))

  private def loadDefaults(projectId: String): IO[Unit] =
    if (projectId.isEmpty) IO(defaultsVar.set(Map.empty))
    else
      tgAiActionService.findDefaults(UUID.fromString(projectId)).map {
        case Left(failure) => toast.error(failure)
        case Right(success) =>
          defaultsVar.set(success.body.getOrElse(List.empty).map(d => d.endpointId -> d.actionId.toString).toMap)
      }

  spinnerService.runWithSpinner(load()).unsafeRunAndForget()

  private def changeDefault(projectId: String, endpointId: Int, actionId: String): Unit = {
    val call =
      if (actionId.isEmpty)
        tgAiActionService.clearDefault(UUID.fromString(projectId), endpointId).toastMessageJustMessage("Default cleared")
      else
        tgAiActionService
          .setDefault(SetTgAiActionDefaultReq(UUID.fromString(projectId), endpointId, UUID.fromString(actionId)))
          .toastMessageJustMessage("Default saved")
    spinnerService.runWithSpinner(call.flatMap(_ => loadDefaults(projectId))).unsafeRunAndForget()
  }

  private val projectSelect = Signal.combine(projectsVar.signal, selectedProjectVar.signal).map { case (projects, _) =>
    TgSelect(
      inputSignal = selectedProjectVar.signal,
      inputStateUpdater = Observer[String] { projectId =>
        selectedProjectVar.set(projectId)
        spinnerService.runWithSpinner(loadDefaults(projectId)).unsafeRunAndForget()
      },
      labelValue = "Project",
      emptyValue = Some("Select a project"),
      valuesAndDisplays = ListMap(projects.map { case (id, name) => id.toString -> name }: _*)
    ).htmlElement
  }

  private def endpointRow(projectId: String, endpoint: TgAiEndpointDto, actions: Seq[FindTgAiActionResp]) =
    TgSelect(
      inputSignal = defaultsVar.signal.map(_.getOrElse(endpoint.id, "")),
      inputStateUpdater = Observer[String](actionId => changeDefault(projectId, endpoint.id, actionId)),
      labelValue = endpoint.name,
      emptyValue = Some("No default"),
      valuesAndDisplays = ListMap(actions.filter(_.endpointIds.contains(endpoint.id)).map(a => a.id.toString -> a.name): _*)
    ).htmlElement.amend(cls("mb-4"))

  val element = div(
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText("Default AI actions per project", TgCard.CardHeaderSize.H3),
        body = TgCard.HtmlCardContent(
          div(
            child <-- projectSelect.map(_.amend(cls("mb-4"))),
            child <-- selectedProjectVar.signal.combineWith(endpointsVar.signal, actionsVar.signal).map {
              case (projectId, endpoints, actions) =>
                if (projectId.isEmpty) div()
                else div(endpoints.map(endpointRow(projectId, _, actions)))
            }
          )
        )
      )
      .element
  )
}
