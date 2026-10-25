package com.togger.ui.project

import com.raquo.laminar.api.L._
import com.togger.dto.FindProjectLabelResp
import com.togger.ui.components.{TgBadge, TgBadgeColor}

object ProjectLabelBadges {

  def apply(labels: Seq[FindProjectLabelResp]): HtmlElement =
    div(
      cls("flex flex-wrap gap-1"),
      labels.sortBy(_.name).map(label => TgBadge(text = label.name, color = TgBadgeColor.Indigo, bordered = true).element)
    )

  /** The project name followed, on the same row, by its labels. */
  def nameWithLabels(name: Modifier[HtmlElement], labels: Seq[FindProjectLabelResp]): HtmlElement =
    div(cls("mr-2 flex flex-wrap items-center gap-2"), name, Option.when(labels.nonEmpty)(apply(labels)))
}
