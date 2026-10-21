package com.togger.ui.tasktemplate

import com.togger.dto.FindTaskTemplateResp

import java.util.UUID

/** Decides when a template seeds the description of a task being created.
  *
  * A description counts as untouched while it is blank or still exactly the body of the template applied last, so
  * switching labels may replace it but never overwrites what the author wrote.
  */
object TaskTemplateSelection {

  /** The template of the label selected last, if the change added a label that has one and the description may be
    * replaced. When several labels are added at once the order is arbitrary, so the pick is made deterministic.
    */
  def onLabelsChanged(
    templates: Seq[FindTaskTemplateResp],
    labelsBefore: Set[UUID],
    labelsAfter: Set[UUID],
    description: String,
    appliedBody: Option[String]
  ): Option[FindTaskTemplateResp] =
    if (!isUntouched(description, appliedBody)) None
    else
      (labelsAfter -- labelsBefore).toSeq
        .sortBy(_.toString)
        .reverse
        .flatMap(labelId => templates.find(_.taskLabelId.contains(labelId)))
        .headOption

  /** The description a pick in the Template dropdown sets; "None" clears it. */
  def descriptionFor(choice: Option[FindTaskTemplateResp]): String = choice.fold("")(_.body)

  def isUntouched(description: String, appliedBody: Option[String]): Boolean =
    description.trim.isEmpty || appliedBody.contains(description)
}
