package com.togger.dto.organization

import com.togger.dto.SearchAccountDto

case class CreateOrganizationReq(name: String, description: String, membersToAdd: Seq[Long])
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
  membersToAdd: Seq[Long],
  membersToRemove: Seq[Long]
)
