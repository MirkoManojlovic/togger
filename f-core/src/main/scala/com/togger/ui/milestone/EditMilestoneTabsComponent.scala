package com.togger.ui.milestone

import com.raquo.laminar.api.L._
import com.togger.ui.components.TgTab
import com.togger.ui.components.TgTabs

/** The edit page of a milestone: its basic information in one tab, its steps in the other. */
class EditMilestoneTabsComponent(basicInformation: HtmlElement, steps: HtmlElement) {

  private val selectedTabVar = Var(EditMilestoneTabsComponent.BasicInformationTabId)

  val element: HtmlElement =
    new TgTabs(
      Seq(
        TgTab(EditMilestoneTabsComponent.BasicInformationTabId, "Basic Information", basicInformation),
        TgTab(EditMilestoneTabsComponent.StepsTabId, "Steps", steps)
      ),
      selectedTabVar
    ).element.amend(cls("w-full"))
}

object EditMilestoneTabsComponent {
  val BasicInformationTabId = "basic-information"
  val StepsTabId = "steps"
}
