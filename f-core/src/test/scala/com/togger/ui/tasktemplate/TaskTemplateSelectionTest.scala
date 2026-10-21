package com.togger.ui.tasktemplate

import com.togger.dto.FindTaskTemplateResp
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant
import java.util.UUID

class TaskTemplateSelectionTest extends AnyWordSpec with Matchers {

  private val projectId = UUID.randomUUID()
  private val bugLabel = UUID.randomUUID()
  private val featureLabel = UUID.randomUUID()
  private val plainLabel = UUID.randomUUID()

  private def template(name: String, body: String, label: Option[UUID]) =
    FindTaskTemplateResp(UUID.randomUUID(), projectId, name, body, label, None, UUID.randomUUID(), Instant.EPOCH)

  private val bug = template("bug", "## Steps", Some(bugLabel))
  private val feature = template("feature", "## Scope", Some(featureLabel))
  private val templates = Seq(bug, feature)

  "TaskTemplateSelection.onLabelsChanged" should {

    "pick the template of the added label when the description is empty" in {
      TaskTemplateSelection.onLabelsChanged(templates, Set.empty, Set(bugLabel), "", None) shouldBe Some(bug)
    }

    "treat a blank description as untouched" in {
      TaskTemplateSelection.onLabelsChanged(templates, Set.empty, Set(bugLabel), "  \n", None) shouldBe Some(bug)
    }

    "switch to the template of the label added last" in {
      TaskTemplateSelection.onLabelsChanged(
        templates,
        Set(bugLabel),
        Set(bugLabel, featureLabel),
        bug.body,
        Some(bug.body)
      ) shouldBe Some(feature)
    }

    "keep a description the author has edited" in {
      TaskTemplateSelection.onLabelsChanged(
        templates,
        Set.empty,
        Set(bugLabel),
        "my own text",
        None
      ) shouldBe None
      TaskTemplateSelection.onLabelsChanged(
        templates,
        Set.empty,
        Set(bugLabel),
        bug.body + " edited",
        Some(bug.body)
      ) shouldBe None
    }

    "do nothing for a label without a template" in {
      TaskTemplateSelection.onLabelsChanged(templates, Set.empty, Set(plainLabel), "", None) shouldBe None
    }

    "do nothing when a label is removed" in {
      TaskTemplateSelection.onLabelsChanged(templates, Set(bugLabel), Set.empty, bug.body, Some(bug.body)) shouldBe None
    }
  }

  "TaskTemplateSelection.descriptionFor" should {

    "give the body of the chosen template" in {
      TaskTemplateSelection.descriptionFor(Some(bug)) shouldBe bug.body
    }

    "clear the description for None" in {
      TaskTemplateSelection.descriptionFor(None) shouldBe ""
    }
  }
}
