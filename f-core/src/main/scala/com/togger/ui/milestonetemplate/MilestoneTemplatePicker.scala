package com.togger.ui.milestonetemplate

import com.raquo.laminar.api.L._
import com.togger.dto.FindMilestoneTemplateResp
import com.togger.ui.auth.TgHtmlTypes.HtmlDiv
import com.togger.ui.components.DropdownMeta
import com.togger.ui.components.TgSelectDropdown
import com.togger.ui.components.TgSelectDropdownOption

import java.util.UUID

/** The "Template" dropdown of the create milestone form: the project's milestone templates and "None". */
object MilestoneTemplatePicker {

  private val NoneValue = ""

  def apply(
    templates: Signal[Seq[FindMilestoneTemplateResp]],
    selected: Signal[Option[UUID]],
    onPick: Observer[Option[FindMilestoneTemplateResp]]
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
