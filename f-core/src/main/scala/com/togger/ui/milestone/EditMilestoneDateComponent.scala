package com.togger.ui.milestone

import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveHtmlElement
import com.togger.common.TgJavaTimeSyntax.TgInstantOps
import com.togger.dto.FindMilestoneDateResp
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.TgInputType
import com.togger.ui.auth.TgTextArea.TgTextAreaSyntax
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro
import org.scalajs.dom.HTMLDivElement

import java.time.{Instant, LocalDate, ZoneOffset}
import java.util.UUID

case class EditMilestoneDateForm(
  id: UUID,
  name: String = "",
  description: String = "",
  date: String = ""
) {
  lazy val dateAsInstant: Instant =
    LocalDate.parse(date).atTime(0, 0, 0).toInstant(ZoneOffset.UTC)
}

object EditMilestoneDateForm {
  def fromDto(dto: FindMilestoneDateResp): EditMilestoneDateForm =
    EditMilestoneDateForm(
      id = dto.id,
      name = dto.name,
      description = dto.description,
      date = dto.date.toLocalDate().toString
    )
}

class EditMilestoneDateComponent(dto: FindMilestoneDateResp) {
  val formState = Var(EditMilestoneDateForm.fromDto(dto))

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

  override def toString = s"EditMilestoneDateComponent(${formState.now()})"
}
