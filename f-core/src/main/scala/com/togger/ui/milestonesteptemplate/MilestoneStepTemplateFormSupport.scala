package com.togger.ui.milestonesteptemplate

import java.util.UUID

case class MilestoneStepTemplateForm(
  projectId: UUID = UUID.randomUUID(),
  id: UUID = UUID.randomUUID(),
  name: String = "",
  description: String = ""
)

object MilestoneStepTemplateFormSupport {

  val descriptionHint: String = "What this step is about\n\n**Exit criteria**\n- First criterion"

}
