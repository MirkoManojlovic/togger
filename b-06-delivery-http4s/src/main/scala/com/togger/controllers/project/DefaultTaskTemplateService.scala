package com.togger.controllers.project

import com.togger.common.TgClock
import com.togger.task.{DefaultTaskLabel, TaskLabel}
import com.togger.tasktemplate.{TaskTemplate, TaskTemplateVersion}

import java.util.UUID

class DefaultTaskTemplateService(tgClock: TgClock) {

  /** A template with its first version for each of the labels that has a default one, in the order of the labels. */
  def createDefaultTaskTemplates(
    projectId: UUID,
    createdBy: UUID,
    labels: Seq[TaskLabel]
  ): Seq[(TaskTemplate, TaskTemplateVersion)] = {
    val now = tgClock.currentInstant
    labels.flatMap { label =>
      DefaultTaskTemplateService.contentOf(label.name).map { case (name, body) =>
        val templateId = UUID.randomUUID()
        TaskTemplate(templateId, projectId, Some(label.id), createdBy, now) ->
          TaskTemplateVersion(UUID.randomUUID(), templateId, name, body, now)
      }
    }
  }
}

object DefaultTaskTemplateService {

  val featureBody: String =
    """**Summary**  
      |Upgrade Node runtime to 22.x on staging
      |
      |**Description**  
      |A detailed description of the task, including the context, requirements and constraints needed to complete it.
      |
      |**Outcome**  
      |Staging environment runs Node 22 with no test regressions.
      |
      |**In scope**  
      |-  Update the Dockerfile
      |-  Refresh the lockfile
      |- Run the full test suite
      |- Update the CI base image
      |
      |**Out of scope**  
      |- Production rollout (tracked separately) 
      |
      |**Dependencies**  
      |- SRE approval for the staging window
      |- Lockfile audit completed
      |- CI base image published
      |
      |**Exit criteria**
      |- All tests pass
      |- CI green for 24 hours
      |- Sign-off from SRE
      |
      |---
      |**Steps**
      |
      |- [ ] Step 1
      |- [ ] Step 2
      |- [ ] Step 3
      |""".stripMargin

  val bugBody: String =
    """**Summary**  
      |Login button does nothing on Safari 17
      |
      |**Description**  
      |We identified a bug in the checkout flow that blocks paying customers from completing an order.
      |
      |**Steps to reproduce**  
      |- Open the login page in Safari 17.
      |-  Enter valid credentials.
      |- Click Sign in.
      |
      |**Actual behavior**  
      |Nothing happens. No network request fires and no error is shown.
      |
      |**Expected behavior**  
      |User is redirected to the dashboard within a second.
      |
      |**Impact**  
      |All Safari users on the Pro plan, roughly 8 percent of daily sign-ins.
      |
      |**Environment**
      |macOS 14.4, Safari 17.4, build 2026.5.21
      |
      |**Additional notes**
      |Reproducible on every attempt. Workaround: reload the tab.
      |""".stripMargin

  val docsBody: String =
    """**Summary**  
      |Document the webhook retry policy in the API guide
      |
      |**Description**  
      |A detailed description of the documentation gap, who it affects and why it needs to be filled or corrected now.
      |
      |**Audience**  
      |Integration engineers building against the public API.
      |
      |**Outcome**  
      |The API guide explains retry timing, limits and failure handling, with a working example.
      |
      |**In scope**  
      |- New "Retries" section in the webhooks page
      |- Example payload and response codes
      |- Link from the troubleshooting page
      |
      |**Out of scope**  
      |- Changes to the retry behavior itself
      |- Translations
      |
      |**Sources**  
      |- Webhook dispatcher source code
      |- Notes from the platform team
      |
      |**Exit criteria**
      |- Content reviewed by a subject-matter expert
      |- Code samples run as written
      |- Links and formatting checked
      |- Docs site builds without warnings
      |
      |---
      |**Steps**
      |
      |- [ ] Step 1
      |- [ ] Step 2
      |- [ ] Step 3
      |""".stripMargin

  val testBody: String =
    """**Summary**  
      |Add integration tests for the task creation endpoint
      |
      |**Description**  
      |A detailed description of the testing gap, including the risk it leaves uncovered and any recent incidents it relates to.
      |
      |**Outcome**  
      |The task creation flow is covered by reliable automated tests that fail when the behavior regresses.
      |
      |**Test scope**  
      |- Happy path with a valid payload
      |- Validation errors for missing and malformed fields
      |- Authorization failures
      |- Duplicate and concurrent requests
      |
      |**Out of scope**  
      |- Performance and load testing
      |- Frontend component tests
      |
      |**Test type**  
      |Integration (unit / integration / end-to-end / performance)
      |
      |**Environment and test data**  
      |Local Postgres test database, seeded organization and project fixtures.
      |
      |**Exit criteria**
      |- New tests pass locally and in CI
      |- Tests are deterministic over repeated runs
      |- Tests fail when the covered behavior is broken
      |- No unrelated production code changes
      |
      |---
      |**Steps**
      |
      |- [ ] Step 1
      |- [ ] Step 2
      |- [ ] Step 3
      |""".stripMargin

  val refactorBody: String =
    """**Summary**  
      |Extract task status transitions out of TaskService
      |
      |**Description**  
      |A detailed description of the current structure, why it is hard to work with, and what the code should look like afterwards.
      |
      |**Motivation**  
      |Status rules are spread across several methods, which makes them hard to test and easy to break.
      |
      |**Outcome**  
      |Transition rules live in one small, pure unit with no change to external behavior.
      |
      |**In scope**  
      |- Move transition logic into a dedicated module
      |- Update callers
      |- Add or adjust tests that pin the current behavior
      |
      |**Out of scope**  
      |- New features or behavior changes
      |- Database schema changes
      |
      |**Risks**  
      |- Subtle behavior differences in edge-case transitions
      |- Merge conflicts with in-flight work on the same files
      |
      |**Exit criteria**
      |- Existing tests pass without modification to their assertions
      |- Behavior is unchanged for API consumers
      |- Code is simpler, with no dead code left behind
      |
      |---
      |**Steps**
      |
      |- [ ] Step 1
      |- [ ] Step 2
      |- [ ] Step 3
      |""".stripMargin

  val choreBody: String =
    """**Summary**  
      |Bump sbt and scalafmt versions
      |
      |**Description**  
      |A detailed description of the maintenance work and why it is needed now, for example a deprecation, a security advisory or recurring housekeeping.
      |
      |**Outcome**  
      |The tooling is up to date and the build behaves exactly as before.
      |
      |**In scope**  
      |- Update the versions in the build files
      |- Reformat code if the new formatter requires it
      |- Verify that the build and tests still pass
      |
      |**Out of scope**  
      |- Feature or behavior changes
      |- Unrelated dependency upgrades
      |
      |**Dependencies**  
      |- None
      |
      |**Exit criteria**
      |- Build and CI are green
      |- No functional changes introduced
      |- Follow-up work, if any, is filed separately
      |
      |---
      |**Steps**
      |
      |- [ ] Step 1
      |- [ ] Step 2
      |- [ ] Step 3
      |""".stripMargin

  def contentOf(labelName: String): Option[(String, String)] =
    labelName match {
      case DefaultTaskLabel.Feature.labelName  => Some("Feature" -> featureBody)
      case DefaultTaskLabel.Bug.labelName      => Some("Bug" -> bugBody)
      case DefaultTaskLabel.Docs.labelName     => Some("Docs" -> docsBody)
      case DefaultTaskLabel.Test.labelName     => Some("Test" -> testBody)
      case DefaultTaskLabel.Refactor.labelName => Some("Refactor" -> refactorBody)
      case DefaultTaskLabel.Chore.labelName    => Some("Chore" -> choreBody)
      case _                                   => None
    }
}
