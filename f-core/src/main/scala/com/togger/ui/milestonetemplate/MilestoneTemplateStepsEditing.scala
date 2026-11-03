package com.togger.ui.milestonetemplate

/** The edits of an ordered list of steps; an index out of range changes nothing. */
object MilestoneTemplateStepsEditing {

  def add[A](steps: List[A], step: A): List[A] =
    steps :+ step

  def remove[A](steps: List[A], index: Int): List[A] =
    if (steps.indices.contains(index)) steps.patch(index, Nil, 1) else steps

  /** Moves the step `delta` places down (positive) or up (negative); it stops at the ends of the list. */
  def move[A](steps: List[A], index: Int, delta: Int): List[A] = {
    val target = index + delta
    if (!steps.indices.contains(index) || !steps.indices.contains(target)) steps
    else steps.updated(index, steps(target)).updated(target, steps(index))
  }
}

/** The edits of steps grouped in stages: the outer list is the sequence of stages, the steps of an inner list run in
  * parallel. A step is addressed by its stage and its index inside the stage; an address out of range changes nothing,
  * and a stage left empty is dropped.
  */
object MilestoneStageEditing {

  type Stages[A] = List[List[A]]

  /** One step with the address and the label it is shown with. */
  case class Row[A](step: A, stage: Int, indexInStage: Int, label: String)

  /** Stages of single steps: the way steps ran before they could run in parallel. */
  def sequential[A](steps: List[A]): Stages[A] = steps.map(List(_))

  /** The step in a stage of its own after the last stage. */
  def add[A](stages: Stages[A], step: A): Stages[A] =
    stages :+ List(step)

  def remove[A](stages: Stages[A], stage: Int, index: Int): Stages[A] =
    if (!exists(stages, stage, index)) stages
    else withoutEmpty(stages.updated(stage, stages(stage).patch(index, Nil, 1)))

  /** Moves the step `delta` places down (positive) or up (negative). A step alone in its stage swaps places with the
    * neighbouring stage; a step in a parallel stage leaves it for a stage of its own just before or after it.
    */
  def move[A](stages: Stages[A], stage: Int, index: Int, delta: Int): Stages[A] =
    if (!exists(stages, stage, index) || delta == 0) stages
    else if (stages(stage).size == 1) swapStages(stages, stage, stage + delta)
    else {
      val step = stages(stage)(index)
      val rest = stages.updated(stage, stages(stage).patch(index, Nil, 1))
      rest.patch(if (delta < 0) stage else stage + 1, List(List(step)), 0)
    }

  /** The step joins the stage before its own and runs in parallel with the steps of it. */
  def joinPreviousStage[A](stages: Stages[A], stage: Int, index: Int): Stages[A] =
    if (stage < 1 || !exists(stages, stage, index)) stages
    else {
      val step = stages(stage)(index)
      withoutEmpty(
        stages.updated(stage - 1, stages(stage - 1) :+ step).updated(stage, stages(stage).patch(index, Nil, 1))
      )
    }

  /** A step of a parallel stage leaves it for a stage of its own just after it; a step alone in its stage stays. */
  def splitFromStage[A](stages: Stages[A], stage: Int, index: Int): Stages[A] =
    if (!exists(stages, stage, index) || stages(stage).size == 1) stages
    else move(stages, stage, index, 1)

  /** The steps in the order they are shown, with the label of the stage: `1`, `2` for a step alone in its stage and
    * `3a`, `3b` for the steps of a parallel stage.
    */
  def rows[A](stages: Stages[A]): List[Row[A]] =
    stages.zipWithIndex.flatMap { case (inStage, stage) =>
      inStage.zipWithIndex.map { case (step, index) => Row(step, stage, index, label(stage, index, inStage.size)) }
    }

  def label(stage: Int, index: Int, stageSize: Int): String =
    if (stageSize <= 1) s"${stage + 1}" else s"${stage + 1}${('a' + index).toChar}"

  private def exists[A](stages: Stages[A], stage: Int, index: Int): Boolean =
    stages.indices.contains(stage) && stages(stage).indices.contains(index)

  private def swapStages[A](stages: Stages[A], from: Int, to: Int): Stages[A] =
    if (!stages.indices.contains(to)) stages else stages.updated(from, stages(to)).updated(to, stages(from))

  private def withoutEmpty[A](stages: Stages[A]): Stages[A] = stages.filter(_.nonEmpty)
}
