package com.togger.milestone

import com.togger.milestone.DefaultMilestoneStepTemplates._

/** A default milestone template: its name and description and the names of its default step templates in order. */
case class DefaultMilestoneTemplate(name: String, description: String, stepNames: List[String])

/** The milestone templates every new project starts with, built from its default step templates. */
object DefaultMilestoneTemplates {

  val NewFeature = "New feature"
  val Migration = "Migration"
  val BugFix = "Bug fix"
  val ResearchSpike = "Research spike"
  val Release = "Release"
  val Refactoring = "Refactoring"

  val defaults: Seq[DefaultMilestoneTemplate] = Seq(
    DefaultMilestoneTemplate(
      NewFeature,
      "Deliver a new capability: design it, prove the risky part, build it, then stage and release it.",
      List(Design, Prototype, Development, Staging, Production)
    ),
    DefaultMilestoneTemplate(
      Migration,
      "Move data or a system to a new home. Use the prototype to rehearse the migration before the real run.",
      List(Design, Prototype, Development, Staging, Production)
    ),
    DefaultMilestoneTemplate(
      BugFix,
      "Fix a defect, verify the fix in staging and ship it. Suited for hotfixes.",
      List(Development, Staging, Production)
    ),
    DefaultMilestoneTemplate(
      ResearchSpike,
      "Time-boxed investigation of an unknown: write down the options and test the most promising one.",
      List(Design, Prototype)
    ),
    DefaultMilestoneTemplate(
      Release,
      "Ship work that is already built: verify it in staging and roll it out.",
      List(Staging, Production)
    ),
    DefaultMilestoneTemplate(
      Refactoring,
      "Improve the structure of existing code without changing its behavior.",
      List(Design, Development, Staging)
    )
  )
}
