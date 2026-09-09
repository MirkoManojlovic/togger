package com.togger.ui.milestone

import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveHtmlElement
import com.togger.auth.SecuredEndpoint
import com.togger.backendcalls.ProtectedBackendClient
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.dto.{CreateMilestoneDateReq, FindMilestoneDateResp}
import com.togger.milestone.MilestoneDateEndpoints
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.TgInputType
import com.togger.ui.auth.TgTextArea.TgTextAreaSyntax
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro
import org.scalajs.dom.HTMLDivElement

import java.time.{Instant, LocalDate, ZoneOffset}

case class CreateMilestoneDateForm(
  name: String = "",
  description: String = "",
  date: String = ""
) {
  lazy val dateAsInstant: Instant =
    LocalDate.parse(date).atTime(0, 0, 0).toInstant(ZoneOffset.UTC)
}

class CreateMilestoneDateComponent {
  val formState = Var(CreateMilestoneDateForm())

  private val nameInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.name),
    errors = EventStream.empty,
    labelValue = "Milestone date name",
    tgInputType = TgInputType.Text,
    isRequired = false
  )

  private val descriptionTextArea = formState.tgTextAreaManualErrorSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.description),
    errors = EventStream.empty,
    labelValue = "Description",
    isRequired = false,
    numberOfRows = 2
  )

  private val dateInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.date),
    errors = EventStream.empty,
    labelValue = "Date",
    tgInputType = TgInputType.Date,
    isRequired = false
  )

  val htmlElement: ReactiveHtmlElement[HTMLDivElement] =
    div(
      cls("mb-4"),
      nameInput.htmlElement.amend(cls("mb-4")),
      descriptionTextArea.htmlElement.amend(cls("mb-4")),
      dateInput.htmlElement
    )

  override def toString = s"CreateMilestoneDateComponent(${formState.now()})"
}

class MilestoneDateFeService(securedEndpoint: SecuredEndpoint, protectedBackendClient: ProtectedBackendClient) {
  val milestoneDateEndpoints = new MilestoneDateEndpoints(securedEndpoint)

  def createMilestoneDate(dto: CreateMilestoneDateReq): TgRespIO[FindMilestoneDateResp] =
    protectedBackendClient.protectedHttpCall(milestoneDateEndpoints.saveEndpoint)(dto)
}
