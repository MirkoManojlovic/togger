package com.togger.dto.organization

import com.togger.dto.SearchAccountDto

import java.util.UUID

case class CreateOrganizationReq(name: String, description: String, icon: String, membersToAdd: Seq[UUID])
case class FindOrganizationResp(
  id: UUID,
  name: String,
  description: String,
  icon: String,
  members: Seq[SearchAccountDto]
)

case class FindOrganizationsAndProjects(
  id: UUID,
  name: String,
  description: String,
  icon: String,
  projects: Seq[OrganizationProjectResp]
)

case class OrganizationProjectResp(
  id: UUID,
  name: String,
  description: String,
  icon: String,
  workDays: Set[Int] // ISO day-of-week values, Monday=1..Sunday=7
)

case class UpdateOrganizationReq(
  id: UUID,
  name: String,
  description: String,
  icon: String,
  membersToAdd: Seq[UUID],
  membersToRemove: Seq[UUID]
)
