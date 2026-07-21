package com.togger.dto.organization

import com.togger.dto.SearchAccountDto

import java.util.UUID

case class CreateOrganizationReq(name: String, description: String, membersToAdd: Seq[UUID])
case class FindOrganizationResp(
  id: UUID,
  name: String,
  description: String,
  members: Seq[SearchAccountDto]
)

case class FindOrganizationsAndProjects(
  id: UUID,
  name: String,
  description: String,
  projects: Seq[OrganizationProjectResp]
)

case class OrganizationProjectResp(
  id: UUID,
  name: String,
  description: String
)

case class UpdateOrganizationReq(
  id: UUID,
  name: String,
  description: String,
  membersToAdd: Seq[UUID],
  membersToRemove: Seq[UUID]
)
