package com.togger.milestone

/** The step templates every new project starts with: name and description, in the order the steps usually happen. */
object DefaultMilestoneStepTemplates {

  val Design = "Design"
  val Prototype = "Prototype"
  val Development = "Development"
  val Staging = "Staging"
  val Production = "Production"

  val designDescription: String =
    """Define what will be built and how, before any real code is written.
      |
      |**Outcome**
      |A reviewed design that the team agrees to build.
      |
      |**Exit criteria**
      |- Requirements and scope are written down
      |- Open questions and risks are listed
      |- The approach is reviewed by the team
      |""".stripMargin

  val prototypeDescription: String =
    """Build a small, throwaway version to prove the riskiest part of the design.
      |
      |**Outcome**
      |Evidence that the approach works, or a reason to change it.
      |
      |**Exit criteria**
      |- The riskiest assumption is tested
      |- Findings are written down
      |- The design is adjusted where the prototype disagreed with it
      |""".stripMargin

  val developmentDescription: String =
    """Implement the agreed design with tests.
      |
      |**Outcome**
      |The complete change, merged and ready to be deployed to staging.
      |
      |**Exit criteria**
      |- All planned tasks are done
      |- Automated tests pass
      |- The change is reviewed and merged
      |""".stripMargin

  val stagingDescription: String =
    """Run the change in a production-like environment and verify it.
      |
      |**Outcome**
      |Confidence that the change behaves correctly with realistic data and configuration.
      |
      |**Exit criteria**
      |- Deployed to staging without errors
      |- Acceptance checks and regression checks pass
      |- Stakeholders signed off
      |""".stripMargin

  val productionDescription: String =
    """Roll the change out to production and watch it.
      |
      |**Outcome**
      |The change is live and behaving as expected.
      |
      |**Exit criteria**
      |- Deployed to production
      |- Monitoring and alerts show no regressions
      |- Rollback plan was ready and is no longer needed
      |""".stripMargin

  /** Name and description of each default step template, in the order they usually happen. */
  val defaults: Seq[(String, String)] = Seq(
    Design -> designDescription,
    Prototype -> prototypeDescription,
    Development -> developmentDescription,
    Staging -> stagingDescription,
    Production -> productionDescription
  )
}
