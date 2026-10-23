package com.togger.ui.tasktemplate

import com.raquo.laminar.api.L._
import com.togger.dto.{FindTaskLabelResp, FindTaskTemplateResp}
import com.togger.ui.auth.TgHtmlTypes.HtmlDiv
import com.togger.ui.components.TaskLabelBadgeColor.TaskLabelBadgeColorOps
import com.togger.ui.components.{DropdownMeta, TgSelectDropdown, TgSelectDropdownOption}

import java.util.UUID

case class TaskTemplateForm(
  projectId: UUID = UUID.randomUUID(),
  id: UUID = UUID.randomUUID(),
  name: String = "",
  body: String = "",
  taskLabelId: Option[UUID] = None
)

object TaskTemplateFormSupport {

  val bodyHint: String = "## Description\n\n## Steps\n- [ ] First step"

  private val NoLabelValue = ""

  /** A label has at most one template, so only labels without one are offered, plus the one this template has. */
  def selectableLabels(
    labels: Seq[FindTaskLabelResp],
    templates: Seq[FindTaskTemplateResp],
    ownLabelId: Option[UUID]
  ): Seq[FindTaskLabelResp] = {
    val taken = templates.flatMap(_.taskLabelId).toSet -- ownLabelId
    labels.filterNot(label => taken.contains(label.id)).sortBy(_.name)
  }

  def labelPicker(selectable: Signal[Seq[FindTaskLabelResp]], form: Var[TaskTemplateForm]): Signal[HtmlDiv] =
    selectable.map { labels =>
      TgSelectDropdown(
        inputSignal = form.signal.map(_.taskLabelId.fold(NoLabelValue)(_.toString)),
        inputStateUpdater = Observer[String] { value =>
          form.update(_.copy(taskLabelId = if (value == NoLabelValue) None else Some(UUID.fromString(value))))
        },
        labelValue = "Task label",
        options = TgSelectDropdownOption(NoLabelValue, DropdownMeta("No label", ""), () => span("No label")) +:
          labels.map(label =>
            TgSelectDropdownOption(label.id.toString, DropdownMeta(label.badgeText, ""), () => span(label.badgeText))
          ),
        placeholder = "No label"
      ).htmlElement
    }
}
