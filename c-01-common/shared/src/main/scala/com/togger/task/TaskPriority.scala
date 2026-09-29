package com.togger.task

import enumeratum.values.{IntEnum, IntEnumEntry}

sealed abstract class TaskPriority(val value: Int, val shortCode: String, val priorityName: String, val description: String)
    extends IntEnumEntry

object TaskPriority extends IntEnum[TaskPriority] {

  case object Critical
      extends TaskPriority(
        1,
        "p1",
        "Critical",
        "Immediate response required; system outage, security breach, or complete business disruption. " +
          "SLA: Acknowledge within 15 minutes, resolve within 1-2 hours."
      )
  case object High
      extends TaskPriority(
        2,
        "p2",
        "High",
        "Severe degradation or major malfunction affecting many users, but a workaround exists. " +
          "SLA: Acknowledge within 1 hour, resolve within 4-8 hours."
      )
  case object Medium
      extends TaskPriority(
        3,
        "p3",
        "Medium",
        "Minor issues impacting efficiency or a single department; core operations continue. " +
          "SLA: Acknowledge within 4 hours, resolve within 24-48 hours."
      )
  case object Low
      extends TaskPriority(
        4,
        "p4",
        "Low",
        "Minor inconveniences or routine requests with no immediate business impact. " +
          "SLA: Acknowledge within 24 hours, resolve within 5-7 days."
      )
  case object Planned
      extends TaskPriority(
        5,
        "p5",
        "Planned",
        "Proactive maintenance, enhancements, or informational requests. " +
          "SLA: Acknowledge within 1 business day, resolve within 10 days."
      )

  def namesToValuesMapString: Map[String, String] =
    this.values.map(p => p.value.toString -> s"${p.shortCode.toUpperCase} - ${p.priorityName}").toMap

  override def values: IndexedSeq[TaskPriority] = findValues

}
