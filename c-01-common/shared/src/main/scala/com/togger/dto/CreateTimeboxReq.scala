package com.togger.dto

import java.time.{Instant, LocalDate}
import java.util.UUID

case class CreateTimeboxReq(
  projectId: UUID,
  startDate: LocalDate,
  endDate: LocalDate,
  description: Option[String] = None,
  taskIds: Seq[UUID] = Seq.empty
)

/** Saves a new version of an existing timebox, a full snapshot of the plan. */
case class SaveTimeboxVersionReq(
  timeboxId: UUID,
  startDate: LocalDate,
  endDate: LocalDate,
  description: Option[String] = None,
  taskIds: Seq[UUID] = Seq.empty
)

case class TimeboxVersionDto(
  version: UUID,
  timeboxId: UUID,
  startDate: LocalDate,
  endDate: LocalDate,
  description: Option[String],
  createdAt: Instant,
  tasks: List[TaskVersionDto]
)

case class FindTimeboxResp(
  id: UUID,
  projectId: UUID,
  createdAt: Instant,
  latestVersion: TimeboxVersionDto
)
