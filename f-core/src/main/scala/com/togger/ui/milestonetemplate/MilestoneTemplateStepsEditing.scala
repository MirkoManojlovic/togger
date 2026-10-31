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
