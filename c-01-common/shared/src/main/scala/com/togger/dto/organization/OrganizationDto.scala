package com.togger.dto.organization

import com.togger.dto.SearchAccountDto

import java.util.UUID

case class CreateOrganizationReq(name: String, description: String, membersToAdd: Seq[UUID])
case class FindOrganizationResp(
  id: Long,
  name: String,
  description: String,
  members: Seq[SearchAccountDto]
)

case class FindOrganizationsAndProjects(
  id: Long,
  name: String,
  description: String,
  projects: Seq[OrganizationProjectResp]
)

case class OrganizationProjectResp(
  id: Long,
  name: String,
  description: String
)

case class UpdateOrganizationReq(
  id: Long,
  name: String,
  description: String,
  membersToAdd: Seq[UUID],
  membersToRemove: Seq[UUID]
)
