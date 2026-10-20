package com.togger.ui.timebox

import com.togger.common.WorkDayCalendar
import com.togger.dto.{FindTaskRespFull, SearchAccountDto}

import java.time.{DayOfWeek, LocalDate}

/** How full a bar is: green up to 100%, red over it. */
sealed trait CapacityLevel

object CapacityLevel {
  case object Green extends CapacityLevel
  case object Red extends CapacityLevel

  /** Exactly 100% is still green; work with no capacity at all is over it. */
  def of(usedDays: Int, capacityDays: Int): CapacityLevel =
    if (usedDays > capacityDays) Red else Green
}

/** The work days a timebox can hold for someone (or for everyone), and how many of them the staged tasks use.
  *
  * @param overlapDays
  *   the work days on which more than one task is going on; they are used once, however many tasks share them
  */
case class CapacityLine(name: String, usedDays: Int, capacityDays: Int, overlapDays: Int = 0) {

  /** Not capped at 100, so the bar can say how far over it is. */
  def percent: Int = if (capacityDays <= 0) (if (usedDays > 0) 100 else 0) else usedDays * 100 / capacityDays

  def level: CapacityLevel = CapacityLevel.of(usedDays, capacityDays)

  def isOver: Boolean = usedDays > capacityDays

  def hasOverlap: Boolean = overlapDays > 0
}

/** What a timebox can hold: its work days for each person on the project. Tasks can't add up to more work days than
  * the timebox has times the people, nor can one person's tasks to more than the timebox has.
  *
  * A task's size is its own work days, from its start to its due date, wherever they fall relative to the timebox.
  * Tasks that share a work day split it between them, so a day counts once however many tasks fall on it; such days
  * only raise a warning ([[CapacityLine.overlapDays]]). Tasks nobody is assigned to use the team's capacity but have no
  * bar of their own, so [[unassignedDays]] has no limit.
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

  def hasOverlap: Boolean = (total +: people).exists(_.hasOverlap)
}

object TimeboxCapacity {

  val TotalName = "Total capacity"

  def taskWorkDays(task: FindTaskRespFull, workDays: Set[DayOfWeek]): Seq[LocalDate] =
    Iterator
      .iterate(task.startDate)(_.plusDays(1))
      .takeWhile(!_.isAfter(task.dueDate))
      .filter(day => workDays.contains(day.getDayOfWeek))
      .toSeq

  /** The days someone's tasks fall on: each day once, and how many of them have more than one task. */
  private case class Load(used: Int, overlap: Int)

  private def loadOf(tasks: Seq[FindTaskRespFull], workDays: Set[DayOfWeek]): Load = {
    val perDay = tasks.flatMap(taskWorkDays(_, workDays)).groupBy(identity).view.mapValues(_.size)
    Load(perDay.size, perDay.count(_._2 > 1))
  }

  def of(
    tasks: Seq[FindTaskRespFull],
    members: Seq[SearchAccountDto],
    workDays: Set[DayOfWeek],
    timebox: (LocalDate, LocalDate)
  ): TimeboxCapacity = {
    val timeboxWorkDays = WorkDayCalendar.countWorkDays(timebox._1, timebox._2, workDays)
    val byAssignee = tasks.groupBy(_.assignedTo.map(_.id)).view.mapValues(loadOf(_, workDays)).toMap
    val noLoad = Load(0, 0)
    val loads = byAssignee.values.toSeq
    TimeboxCapacity(
      timeboxWorkDays = timeboxWorkDays,
      total = CapacityLine(
        TotalName,
        loads.map(_.used).sum,
        timeboxWorkDays * members.size,
        loads.map(_.overlap).sum
      ),
      people = members.sortBy(_.fullName.toLowerCase).map { member =>
        val load = byAssignee.getOrElse(Some(member.id), noLoad)
        CapacityLine(member.fullName, load.used, timeboxWorkDays, load.overlap)
      },
      unassignedDays = byAssignee.getOrElse(None, noLoad).used
    )
  }
}
