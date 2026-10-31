package com.togger.ui.milestone

import com.togger.milestone.MilestoneStepStatus

import java.time.{DayOfWeek, LocalDate}

/** The pure rules of closing a step: which statuses close it and what its end date has to be. */
object MilestoneStepClose {

  /** What a step can be closed with: Done or Abandoned. */
  def closingStatuses: IndexedSeq[MilestoneStepStatus] = MilestoneStepStatus.closing

  /** Steps that are not closed, in the order they are listed. */
  def openSteps[A](steps: Seq[A], statusOf: A => MilestoneStepStatus): List[A] =
    steps.filterNot(step => statusOf(step).isClosing).toList

  /** True when there is at least one step and all of them are closed. */
  def allClosed(statuses: Seq[MilestoneStepStatus]): Boolean =
    statuses.nonEmpty && statuses.forall(_.isClosing)

  /** Why the end date cannot close the step; nothing when it can. */
  def endDateErrors(endDate: Option[LocalDate], startDate: LocalDate, workDays: Set[DayOfWeek]): List[String] =
    endDate match {
      case None => List("Pick an end date")
      case Some(date) =>
        val beforeStart = if (date.isBefore(startDate)) List("End date must be on or after the start date") else Nil
        val notWorkDay =
          if (workDays.nonEmpty && !workDays.contains(date.getDayOfWeek))
            List("End date must be one of this project's configured work days")
          else Nil
        beforeStart ++ notWorkDay
    }

  /** The comment of a change must not be blank. */
  def commentErrors(comment: String): List[String] =
    if (comment.trim.isEmpty) List("Change comment is required") else Nil
}
