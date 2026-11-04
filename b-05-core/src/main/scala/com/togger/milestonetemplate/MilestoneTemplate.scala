package com.togger.milestonetemplate

import java.time.Instant
import java.util.UUID

case class MilestoneTemplate(id: UUID, projectId: UUID, createdBy: UUID, createdAt: Instant)

case class MilestoneTemplateVersion(
  version: UUID,
  milestoneTemplateId: UUID,
  name: String,
  description: String,
  createdAt: Instant
)

/** A step of a template: the step template it points to, with that step template's current content. `stage` is the
  * index of its stage: steps with the same stage run in parallel.
  */
case class MilestoneTemplateStep(stepTemplateId: UUID, name: String, description: String, stage: Int)

/** A template version with its steps ordered by stage. */
case class MilestoneTemplateVersionWithSteps(version: MilestoneTemplateVersion, steps: List[MilestoneTemplateStep]) {

  /** The step templates of each stage, in the order of the stages. */
  def stages: List[List[UUID]] =
    steps.groupBy(_.stage).toList.sortBy { case (stage, _) => stage }.map { case (_, inStage) =>
      inStage.map(_.stepTemplateId)
    }

  def hasSameContentAs(name: String, description: String, stages: List[List[UUID]]): Boolean =
    version.name == name && version.description == description &&
      MilestoneTemplateVersionWithSteps.sameStages(this.stages, stages)

}

object MilestoneTemplateVersionWithSteps {

  /** The stages are the same when they hold the same step templates in the same order of stages; the order of the steps
    * inside a stage does not matter, as they run in parallel.
    */
  def sameStages(left: List[List[UUID]], right: List[List[UUID]]): Boolean =
    normalized(left) == normalized(right)

  /** Without empty stages and repeated step templates in a stage. */
  def normalized(stages: List[List[UUID]]): List[Set[UUID]] =
    stages.map(_.toSet).filter(_.nonEmpty)
}

/** A template with its latest version and the steps of it. */
case class MilestoneTemplateFull(template: MilestoneTemplate, latest: MilestoneTemplateVersionWithSteps)
