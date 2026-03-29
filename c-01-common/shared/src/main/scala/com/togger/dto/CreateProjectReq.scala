package com.togger.dto

case class CreateProjectReq(
  organizationId: Long,
  name: String,
  description: String,
  membersToAdd: Seq[Long]
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
  membersToAdd: Seq[Long],
  membersToRemove: Seq[Long]
)
