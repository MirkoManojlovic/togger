package com.togger.project
import java.time.{DayOfWeek, Instant}
import java.util.UUID

case class Project(
  id: UUID,
  organizationId: UUID,
  name: String,
  description: String,
  icon: String,
  workDays: Set[DayOfWeek] = Project.DefaultWorkDays,
  createdAt: Instant,
  updatedAt: Instant
)

object Project {
  val DefaultWorkDays: Set[DayOfWeek] =
    Set(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
}

case class ProjectAccount(
  projectId: UUID,
  accountId: UUID,
  createdAt: Instant
)
