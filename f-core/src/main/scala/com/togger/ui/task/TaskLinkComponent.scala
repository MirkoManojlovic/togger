package com.togger.ui.task

import com.togger.dto.FindLinkResp
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.TgInputType
import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveHtmlElement
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro
import org.scalajs.dom.HTMLDivElement

case class TaskLinkFormData(
  id: Option[Long] = None,
  name: String = "",
  url: String = ""
)
object TaskLinkFormData {
  def fromResp(dto: FindLinkResp): TaskLinkFormData =
    TaskLinkFormData(
      id = Some(dto.id),
      name = dto.name,
      url = dto.url
    )
}

class TaskLinkComponent(linkData: Option[TaskLinkFormData] = None) {
  val formState = Var(linkData.getOrElse(TaskLinkFormData()))

  private val linkNameInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.name),
    errors = EventStream.empty,
    labelValue = "Link name",
    tgInputType = TgInputType.Text,
    isRequired = false
  )

  private val urlInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.url),
    errors = EventStream.empty,
    labelValue = "Url",
    tgInputType = TgInputType.Text,
    isRequired = false
  )

  val htmlElement: ReactiveHtmlElement[HTMLDivElement] =
    div(
      cls("flex mb-4"),
      linkNameInput.htmlElement.amend(cls("mr-2 grow-4")),
      urlInput.htmlElement.amend(cls("grow-6"))
    )

  override def toString = s"TaskLinkComponent(${formState.now()})"
}
