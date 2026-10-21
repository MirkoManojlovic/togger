package com.togger.ui.milestone

import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveHtmlElement
import com.togger.common.TgClock
import com.togger.common.TgJavaTimeSyntax.TgInstantOps
import com.togger.dto.FindMilestoneDateResp
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.TgInputType
import com.togger.ui.components.wysiwyg.WYSIWYGTextEditorSplit.WYSIWYGTextEditorSplitSyntax
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

class EditMilestoneDateComponent(dto: FindMilestoneDateResp, milestoneStartDate: Signal[String], tgClock: TgClock) {
  val formState = Var(EditMilestoneDateForm.fromDto(dto))

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
      nameInput.htmlElement.amend(cls("mb-4")),
      dateBadge.element.amend(cls("mb-4")),
      descriptionEditor.htmlElement
    )

  override def toString = s"EditMilestoneDateComponent(${formState.now()})"
}
