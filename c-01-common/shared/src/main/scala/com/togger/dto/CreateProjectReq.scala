package com.togger.dto

import java.util.UUID

case class CreateProjectReq(
  organizationId: Long,
  name: String,
  description: String,
  membersToAdd: Seq[UUID]
)

case class FindProjectResp(
  id: Long,
  organizationId: Long,
  name: String,
  description: String,
  members: Seq[SearchAccountDto]
)

case class UpdateProjectReq(
  id: Long,
  organizationId: Long,
  name: String,
  description: String,
  membersToAdd: Seq[UUID],
  membersToRemove: Seq[UUID]
)
