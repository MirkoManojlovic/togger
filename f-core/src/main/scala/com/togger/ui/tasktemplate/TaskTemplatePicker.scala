package com.togger.ui.tasktemplate

import com.raquo.laminar.api.L._
import com.togger.dto.FindTaskTemplateResp
import com.togger.ui.auth.TgHtmlTypes.HtmlDiv
import com.togger.ui.components.{DropdownMeta, TgSelectDropdown, TgSelectDropdownOption}

import java.util.UUID

/** The "Template" dropdown of the task forms: the project's templates and "None". */
object TaskTemplatePicker {

  private val NoneValue = ""

  def apply(
    templates: Signal[Seq[FindTaskTemplateResp]],
    selected: Signal[Option[UUID]],
    onPick: Observer[Option[FindTaskTemplateResp]]
  ): Signal[HtmlDiv] =
    templates.map { current =>
      TgSelectDropdown(
        inputSignal = selected.map(_.fold(NoneValue)(_.toString)),
        inputStateUpdater = Observer[String] { value =>
          onPick.onNext(current.find(_.id.toString == value))
        },
        labelValue = "Template",
        options = TgSelectDropdownOption(NoneValue, DropdownMeta("None", ""), () => span("None")) +:
          current
            .sortBy(_.name)
            .map(t => TgSelectDropdownOption(t.id.toString, DropdownMeta(t.name, ""), () => span(t.name))),
        placeholder = "None"
      ).htmlElement
    }
}
