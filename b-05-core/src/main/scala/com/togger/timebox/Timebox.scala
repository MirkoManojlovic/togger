package com.togger.timebox

import com.togger.task.TaskVersion

import java.time.{Instant, LocalDate}
import java.util.UUID

case class Timebox(id: UUID, projectId: UUID, createdAt: Instant)

case class TimeboxVersion(
  version: UUID,
  timeboxId: UUID,
  startDate: LocalDate,
  endDate: LocalDate,
  description: Option[String],
  createdAt: Instant
)

/** A timebox version together with the task versions that were planned in it. */
case class TimeboxVersionWithTasks(version: TimeboxVersion, tasks: List[TaskVersion])

case class TimeboxWithOneVersion(timebox: Timebox, version: TimeboxVersionWithTasks)
