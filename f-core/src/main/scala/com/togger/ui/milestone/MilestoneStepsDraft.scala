package com.togger.ui.milestone

import com.togger.common.DateRangeValidation
import com.togger.common.WorkDayCalendar
import com.togger.dto.MilestoneTemplateStepDto
import com.togger.dto.NewMilestoneStepReq

import java.time.DayOfWeek
import java.time.LocalDate
import scala.util.Try

/** A step of a milestone that is being created; the dates are the text of the date inputs. */
case class MilestoneStepDraft(name: String = "", description: String = "", startDate: String = "", dueDate: String = "")

object MilestoneStepsDraft {

  private val dateRangeValidation = new DateRangeValidation

  private val allDays: Set[DayOfWeek] = DayOfWeek.values().toSet

  def parseDate(text: String): Option[LocalDate] = Try(LocalDate.parse(text)).toOption

  /** One draft per step of the template, with the milestone range spread evenly over its stages: the steps of a stage
    * run in parallel, so they get the same dates.
    */
  def fromTemplate(
    steps: Seq[MilestoneTemplateStepDto],
    milestoneStart: String,
    milestoneDue: String,
    workDays: Set[DayOfWeek]
  ): List[MilestoneStepDraft] =
    steps.toList.zip(spreadStages(steps.map(_.stage), milestoneStart, milestoneDue, workDays)).map {
      case (step, (start, due)) =>
        MilestoneStepDraft(step.name, step.description, start, due)
    }

  /** The start and due date of each step, one range per distinct stage in the order of the stages; `stages` is the
    * stage of each step. Empty dates when the milestone range is not a valid one yet.
    */
  def spreadStages(
    stages: Seq[Int],
    milestoneStart: String,
    milestoneDue: String,
    workDays: Set[DayOfWeek]
  ): List[(String, String)] = {
    val stageNumbers = stages.distinct.sorted
    val rangeOfStage = for {
      start <- parseDate(milestoneStart)
      due <- parseDate(milestoneDue)
      ranges = WorkDayCalendar.splitEvenly(start, due, stageNumbers.size, workDays)
      if ranges.size == stageNumbers.size
    } yield stageNumbers.zip(ranges.map { case (from, to) => from.toString -> to.toString }).toMap
    stages.toList.map(stage => rangeOfStage.flatMap(_.get(stage)).getOrElse("" -> ""))
  }

  /** The start and due date of each of `count` steps, one after the other; empty dates when the milestone range is not
    * a valid one yet.
    */
  def spread(
    count: Int,
    milestoneStart: String,
    milestoneDue: String,
    workDays: Set[DayOfWeek]
  ): List[(String, String)] = {
    val ranges = for {
      start <- parseDate(milestoneStart)
      due <- parseDate(milestoneDue)
    } yield WorkDayCalendar.splitEvenly(start, due, count, workDays).map { case (from, to) =>
      from.toString -> to.toString
    }
    ranges.filter(_.size == count).getOrElse(List.fill(count)("" -> ""))
  }

  /** Why the step cannot be created in the milestone; nothing when it can. */
  def errors(
    draft: MilestoneStepDraft,
    milestoneStart: String,
    milestoneDue: String,
    workDays: Set[DayOfWeek]
  ): List[String] = {
    val nameErrors = if (draft.name.trim.isEmpty) List("Step name is required") else Nil
    val dateErrors = (parseDate(draft.startDate), parseDate(draft.dueDate)) match {
      case (Some(start), Some(due)) => rangeErrors(start, due, milestoneStart, milestoneDue, workDays)
      case _                        => List("Start date and due date are required")
    }
    nameErrors ++ dateErrors
  }

  private def rangeErrors(
    start: LocalDate,
    due: LocalDate,
    milestoneStart: String,
    milestoneDue: String,
    workDays: Set[DayOfWeek]
  ): List[String] = {
    val workDayErrors =
      dateRangeValidation.validate(Some(start), Some(due), None, if (workDays.isEmpty) allDays else workDays)
    val milestoneRangeErrors = (parseDate(milestoneStart), parseDate(milestoneDue)) match {
      case (Some(from), Some(to)) => dateRangeValidation.validateWithinRange(start, due, from, to)
      case _                      => Right(())
    }
    List(workDayErrors, milestoneRangeErrors).flatMap(_.left.toOption.toList.flatMap(_.toChain.toList.map(_.message)))
  }

  /** The drafts of each stage as the request wants them, with the index of the stage; the drafts of a stage run in
    * parallel. A draft with a date that cannot be read is left out.
    */
  def toStagedRequests(stages: List[List[MilestoneStepDraft]]): List[NewMilestoneStepReq] =
    stages.zipWithIndex.flatMap { case (drafts, stage) => toRequests(drafts).map(_.copy(stage = Some(stage))) }

  /** The drafts as the request wants them; a draft with a date that cannot be read is left out. */
  def toRequests(drafts: Seq[MilestoneStepDraft]): List[NewMilestoneStepReq] =
    drafts.toList.flatMap { draft =>
      for {
        start <- parseDate(draft.startDate)
        due <- parseDate(draft.dueDate)
      } yield NewMilestoneStepReq(draft.name.trim, draft.description, start, due)
    }
}
