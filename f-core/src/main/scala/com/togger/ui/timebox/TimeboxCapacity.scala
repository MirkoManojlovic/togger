package com.togger.ui.timebox

import com.togger.common.WorkDayCalendar
import com.togger.dto.{FindTaskRespFull, SearchAccountDto}

import java.time.{DayOfWeek, LocalDate}

/** How full a bar is: green below 70%, yellow from 70% to 99%, red at 100% and over. */
sealed trait CapacityLevel

object CapacityLevel {
  case object Green extends CapacityLevel
  case object Yellow extends CapacityLevel
  case object Red extends CapacityLevel

  /** Whole-number math, so exactly 70% and exactly 100% land on the right side of their limit. */
  def of(usedDays: Int, capacityDays: Int): CapacityLevel =
    if (usedDays <= 0) Green
    else if (capacityDays <= 0 || usedDays * 100 >= capacityDays * 100) Red
    else if (usedDays * 10 >= capacityDays * 7) Yellow
    else Green
}

/** The work days a timebox can hold for someone (or for everyone), and how many of them the staged tasks use. */
case class CapacityLine(name: String, usedDays: Int, capacityDays: Int) {

  /** Not capped at 100, so the bar can say how far over it is. */
  def percent: Int = if (capacityDays <= 0) (if (usedDays > 0) 100 else 0) else usedDays * 100 / capacityDays

  def level: CapacityLevel = CapacityLevel.of(usedDays, capacityDays)

  def isOver: Boolean = usedDays > capacityDays
}

/** What a timebox can hold: its work days for each person on the project. Tasks can't add up to more work days than
  * the timebox has times the people, nor can one person's tasks to more than the timebox has.
  *
  * A task's size is its own work days, from its start to its due date, wherever they fall relative to the timebox.
  * Tasks nobody is assigned to use the team's capacity but have no bar of their own, so [[unassignedDays]] has no
  * limit.
  *
  * @param timeboxWorkDays
  *   the work days between the timebox's start and end, both included
  */
case class TimeboxCapacity(
  timeboxWorkDays: Int,
  total: CapacityLine,
  people: Seq[CapacityLine],
  unassignedDays: Int
) {

  def isOverCapacity: Boolean = (total +: people).exists(_.isOver)
}

object TimeboxCapacity {

  val TotalName = "Total capacity"

  def taskDays(task: FindTaskRespFull, workDays: Set[DayOfWeek]): Int =
    WorkDayCalendar.countWorkDays(task.startDate, task.dueDate, workDays)

  def of(
    tasks: Seq[FindTaskRespFull],
    members: Seq[SearchAccountDto],
    workDays: Set[DayOfWeek],
    timebox: (LocalDate, LocalDate)
  ): TimeboxCapacity = {
    val timeboxWorkDays = WorkDayCalendar.countWorkDays(timebox._1, timebox._2, workDays)
    val days = tasks.map(task => task -> taskDays(task, workDays))
    def usedBy(owner: FindTaskRespFull => Boolean): Int = days.collect { case (task, size) if owner(task) => size }.sum
    TimeboxCapacity(
      timeboxWorkDays = timeboxWorkDays,
      total = CapacityLine(TotalName, usedBy(_ => true), timeboxWorkDays * members.size),
      people = members.sortBy(_.fullName.toLowerCase).map { member =>
        CapacityLine(member.fullName, usedBy(_.assignedTo.exists(_.id == member.id)), timeboxWorkDays)
      },
      unassignedDays = usedBy(_.assignedTo.isEmpty)
    )
  }
}
