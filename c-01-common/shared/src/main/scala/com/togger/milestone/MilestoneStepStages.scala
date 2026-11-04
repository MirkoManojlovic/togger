package com.togger.milestone

import java.util.UUID

/** The `position` of a step is its stage: steps with the same position run in parallel, stages run in sequence, and
  * gaps between stages are allowed. The default steps (Shelf) are alone in the first stages and never run in parallel
  * with another step. Shared by the backend and the frontend preview, which must place steps the same way.
  */
object MilestoneStepStages {

  /** What the placement rules need to know about a step of the milestone. */
  case class Staged(stepId: UUID, position: Int, isDefault: Boolean)

  sealed trait Placement

  /** The step runs in parallel with the steps already at `position`. */
  case class JoinStage(position: Int) extends Placement

  /** The step gets a stage of its own at `position`; the steps at `position` or later move down by one. */
  case class NewStage(position: Int) extends Placement

  /** The first position a non-default step may take: the stages below the default steps. */
  def firstFreePosition(steps: Seq[Staged]): Int =
    steps.filter(_.isDefault).map(_.position).maxOption.map(_ + 1).getOrElse(0)

  /** The position after the last stage. */
  def nextPosition(steps: Seq[Staged]): Int =
    steps.map(_.position).maxOption.map(_ + 1).getOrElse(0)

  /** Where a new step goes. Without `position` it goes after the last stage, or into the last stage when `parallel`.
    * With `position` it is inserted there, or joins the stage there when `parallel` and that stage exists.
    */
  def placementOf(steps: Seq[Staged], position: Option[Int], parallel: Boolean): Placement = {
    val firstFree = firstFreePosition(steps)
    val next = nextPosition(steps)
    def stageExists(at: Int) = steps.exists(step => step.position == at && !step.isDefault)
    position.map(_.max(firstFree)) match {
      case None if parallel && stageExists(next - 1) => JoinStage(next - 1)
      case None                                      => NewStage(next)
      case Some(at) if parallel && stageExists(at)   => JoinStage(at)
      case Some(at)                                  => NewStage(at.min(next))
    }
  }

  /** The stage a placement puts the step in. */
  def positionOf(placement: Placement): Int = placement match {
    case JoinStage(at) => at
    case NewStage(at)  => at
  }

  /** The steps that have to move down by one to make room for the placement, with their new position. */
  def movesFor(steps: Seq[Staged], placement: Placement): List[(UUID, Int)] = placement match {
    case JoinStage(_) => Nil
    case NewStage(at) => steps.filter(_.position >= at).map(step => step.stepId -> (step.position + 1)).toList
  }

  /** The positions after a reorder: the default steps keep the first stages, then one stage per group. */
  def reorderedPositions(steps: Seq[Staged], stages: List[List[UUID]]): Map[UUID, Int] = {
    val defaults = steps.filter(_.isDefault).sortBy(_.position).map(_.stepId)
    val base = defaults.size
    val grouped = stages.filter(_.nonEmpty).zipWithIndex.flatMap { case (ids, index) => ids.map(_ -> (base + index)) }
    defaults.zipWithIndex.toMap ++ grouped
  }

  /** The positions of new steps that ask for the given stages: the distinct stages are numbered one after the other, in
    * the order of the requested ones, from `first` (the position after the Shelf step).
    */
  def numberedStages(requested: List[Int], first: Int): List[Int] = {
    val numbers = requested.distinct.sorted.zipWithIndex.toMap
    requested.map(stage => numbers(stage) + first)
  }
}
