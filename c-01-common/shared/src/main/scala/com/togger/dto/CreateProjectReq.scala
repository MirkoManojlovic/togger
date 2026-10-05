package com.togger.dto

import java.util.UUID

case class CreateProjectReq(
  organizationId: UUID,
  name: String,
  description: String,
  icon: String,
  membersToAdd: Seq[UUID],
  projectLabelIds: Seq[UUID] = Seq.empty
)

case class FindProjectResp(
  id: UUID,
  organizationId: UUID,
  name: String,
  description: String,
  icon: String,
  workDays: Set[Int], // ISO day-of-week values, Monday=1..Sunday=7
  members: Seq[SearchAccountDto],
  projectLabels: Seq[FindProjectLabelResp] = Seq.empty
)

case class UpdateProjectReq(
  id: UUID,
  organizationId: UUID,
  name: String,
  description: String,
  icon: String,
  workDays: Set[Int], // ISO day-of-week values, Monday=1..Sunday=7
  membersToAdd: Seq[UUID],
  membersToRemove: Seq[UUID],
  projectLabelIds: Seq[UUID] = Seq.empty
)

case class SearchProjectsByLabelsReq(projectLabelIds: Seq[UUID])
