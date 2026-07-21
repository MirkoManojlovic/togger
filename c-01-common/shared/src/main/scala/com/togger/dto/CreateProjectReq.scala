package com.togger.dto

import java.util.UUID

case class CreateProjectReq(
  organizationId: UUID,
  name: String,
  description: String,
  membersToAdd: Seq[UUID]
)

case class FindProjectResp(
  id: UUID,
  organizationId: UUID,
  name: String,
  description: String,
  members: Seq[SearchAccountDto]
)

case class UpdateProjectReq(
  id: UUID,
  organizationId: UUID,
  name: String,
  description: String,
  membersToAdd: Seq[UUID],
  membersToRemove: Seq[UUID]
)
