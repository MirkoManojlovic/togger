package com.togger.dto

import com.togger.milestone.MilestoneStatus

import java.time.Instant
import java.util.UUID

case class CreateMilestoneReq(projectId: UUID, name: String, description: String)

case class UpdateMilestoneReq(id: UUID, name: String, description: String, status: Option[MilestoneStatus] = None)

case class FindMilestoneResp(
  id: UUID,
  projectId: UUID,
  createdAt: Instant,
  latestVersion: UUID,
  name: String,
  description: String,
  status: MilestoneStatus,
  updatedAt: Instant
)

case class MilestoneVersionDto(
  version: UUID,
  milestoneId: UUID,
  name: String,
  description: String,
  status: MilestoneStatus,
  createdAt: Instant,
  updatedAt: Instant
)

case class CreateMilestoneDateReq(milestoneId: UUID, name: String, description: String, date: Instant)

case class UpdateMilestoneDateReq(id: UUID, name: String, description: String, date: Instant)

case class FindMilestoneDateResp(
  id: UUID,
  milestoneId: UUID,
  createdAt: Instant,
  latestVersion: UUID,
  name: String,
  description: String,
  date: Instant,
  updatedAt: Instant
)

case class MilestoneDateVersionDto(
  version: UUID,
  milestoneDateId: UUID,
  name: String,
  description: String,
  date: Instant,
  createdAt: Instant,
  updatedAt: Instant
)
