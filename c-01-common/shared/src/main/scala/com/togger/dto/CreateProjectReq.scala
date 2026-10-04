package com.togger.dto

import java.util.UUID

case class CreateProjectReq(
  organizationId: UUID,
  name: String,
  description: String,
  icon: String,
  membersToAdd: Seq[UUID]
)

case class FindProjectResp(
  id: UUID,
  organizationId: UUID,
  name: String,
  description: String,
  icon: String,
  workDays: Set[Int], // ISO day-of-week values, Monday=1..Sunday=7
  members: Seq[SearchAccountDto]
)

case class UpdateProjectReq(
  id: UUID,
  organizationId: UUID,
  name: String,
  description: String,
  icon: String,
  workDays: Set[Int], // ISO day-of-week values, Monday=1..Sunday=7
  membersToAdd: Seq[UUID],
  membersToRemove: Seq[UUID]
)
