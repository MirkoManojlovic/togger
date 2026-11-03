package com.togger.milestone

/** The `position` of a step is its stage: steps with the same position run in parallel, stages run in sequence, and
  * gaps between stages are allowed. The default steps (Shelf) are alone in the first stages and never run in parallel
  * with another step.
  */
object MilestoneStepStages {

  sealed trait Placement

  /** The step runs in parallel with the steps already at `position`. */
  case class JoinStage(position: Int) extends Placement

  /** The step gets a stage of its own at `position`; the steps at `position` or later move down by one. */
  case class NewStage(position: Int) extends Placement

  /** The first position a non-default step may take: the stages below the default steps. */
  def firstFreePosition(steps: Seq[MilestoneStepVersion]): Int =
    steps.filter(isDefault).map(_.position).maxOption.map(_ + 1).getOrElse(0)

  /** The position after the last stage. */
  def nextPosition(steps: Seq[MilestoneStepVersion]): Int =
    steps.map(_.position).maxOption.map(_ + 1).getOrElse(0)

  /** Where a new step goes. Without `position` it goes after the last stage, or into the last stage when `parallel`.
    * With `position` it is inserted there, or joins the stage there when `parallel` and that stage exists.
    */
  def placementOf(steps: Seq[MilestoneStepVersion], position: Option[Int], parallel: Boolean): Placement = {
    val firstFree = firstFreePosition(steps)
    val next = nextPosition(steps)
    def stageExists(at: Int) = steps.exists(step => step.position == at && !isDefault(step))
    position.map(_.max(firstFree)) match {
      case None if parallel && stageExists(next - 1) => JoinStage(next - 1)
      case None                                      => NewStage(next)
      case Some(at) if parallel && stageExists(at)   => JoinStage(at)
      case Some(at)                                  => NewStage(at.min(next))
    }
  }

  /** The positions after the reorder: the default steps keep the first stages, then one stage per group. */
  def reorderedPositions(
    steps: Seq[MilestoneStepVersion],
    stages: List[List[java.util.UUID]]
  ): Map[java.util.UUID, Int] = {
    val defaults = steps.filter(isDefault).sortBy(_.position).map(_.milestoneStepId)
    val base = defaults.size
    val grouped = stages.filter(_.nonEmpty).zipWithIndex.flatMap { case (ids, index) => ids.map(_ -> (base + index)) }
    defaults.zipWithIndex.toMap ++ grouped
  }

  private def isDefault(step: MilestoneStepVersion): Boolean = DefaultMilestoneStep.isDefaultName(step.name)

}
