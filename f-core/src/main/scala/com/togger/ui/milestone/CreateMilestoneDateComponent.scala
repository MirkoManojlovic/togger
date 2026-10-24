package com.togger.ui.milestone

import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveHtmlElement
import com.togger.auth.SecuredEndpoint
import com.togger.backendcalls.ProtectedBackendClient
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.{PageRequest, PaginatedResponse}
import com.togger.common.TgClock
import com.togger.dto.{CreateMilestoneDateReq, FindMilestoneDateResp, UpdateMilestoneDateReq}
import com.togger.milestone.MilestoneDateEndpoints
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.TgInputType
import com.togger.ui.components.pagination.PaginatedLoader
import com.togger.ui.components.wysiwyg.WYSIWYGTextEditorSplit.WYSIWYGTextEditorSplitSyntax
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro
import org.scalajs.dom.HTMLDivElement

import java.time.{Instant, LocalDate, ZoneOffset}
import java.util.UUID

case class CreateMilestoneDateForm(
  name: String = "",
  description: String = "",
  date: String = ""
) {
  lazy val dateAsInstant: Instant =
    LocalDate.parse(date).atTime(0, 0, 0).toInstant(ZoneOffset.UTC)
}

class CreateMilestoneDateComponent(milestoneStartDate: Signal[String], tgClock: TgClock) {
  val formState = Var(CreateMilestoneDateForm())

  private val nameInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.name),
    errors = EventStream.empty,
    labelValue = "Milestone date name",
    tgInputType = TgInputType.Text,
    isRequired = false
  )

  private val descriptionEditor = formState.wysiwygSplitEditorSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.description),
    labelValue = "Description",
    minHeightRem = 8,
    minWidthRem = 0
  )

  private val startDateVar = Var("")

  private val dateBadge = new MilestoneDateBadge(
    date = formState.signal.map(_.date),
    currentDate = () => formState.now().date,
    milestoneStartDate = () => startDateVar.now(),
    onDate = picked => formState.update(_.copy(date = picked.toString)),
    tgClock = tgClock
  )

  val htmlElement: ReactiveHtmlElement[HTMLDivElement] =
    div(
      cls("mb-4"),
      milestoneStartDate --> startDateVar,
      nameInput.htmlElement.amend(cls("mb-4 max-w-[60ch]")),
      dateBadge.element.amend(cls("mb-4")),
      descriptionEditor.htmlElement
    )

  override def toString = s"CreateMilestoneDateComponent(${formState.now()})"
}

class MilestoneDateFeService(securedEndpoint: SecuredEndpoint, protectedBackendClient: ProtectedBackendClient) {
  val milestoneDateEndpoints = new MilestoneDateEndpoints(securedEndpoint)

  def createMilestoneDate(dto: CreateMilestoneDateReq): TgRespIO[FindMilestoneDateResp] =
    protectedBackendClient.protectedHttpCall(milestoneDateEndpoints.saveEndpoint)(dto)

  def findByMilestoneId(milestoneId: UUID, page: PageRequest): TgRespIO[PaginatedResponse[FindMilestoneDateResp]] =
    protectedBackendClient.protectedHttpCall(milestoneDateEndpoints.findByMilestoneIdEndpoint)((milestoneId, page))

  /** Every date of the milestone, for the form that edits all of them; see [[PaginatedLoader]] for the bound. */
  def findAllByMilestoneId(milestoneId: UUID): TgRespIO[Seq[FindMilestoneDateResp]] =
    PaginatedLoader.loadAll(findByMilestoneId(milestoneId, _))

  def findByProjectId(projectId: UUID, page: PageRequest): TgRespIO[PaginatedResponse[FindMilestoneDateResp]] =
    protectedBackendClient.protectedHttpCall(milestoneDateEndpoints.findByProjectIdEndpoint)((projectId, page))

  /** Every date of the project's milestones, for the history chart; see [[PaginatedLoader]] for the bound. */
  def findAllByProjectId(projectId: UUID): TgRespIO[Seq[FindMilestoneDateResp]] =
    PaginatedLoader.loadAll(findByProjectId(projectId, _))

  def updateMilestoneDate(dto: UpdateMilestoneDateReq): TgRespIO[FindMilestoneDateResp] =
    protectedBackendClient.protectedHttpCall(milestoneDateEndpoints.updateEndpoint)(dto)
}
