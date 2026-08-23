package com.togger.dto.projectmapping

import java.time.Instant
import java.util.UUID

case class CreateProjectMappingReq(projectIdOrigin: UUID, projectIdLoaded: UUID)

case class UpdateProjectMappingReq(id: UUID, projectIdOrigin: UUID, projectIdLoaded: UUID)

case class ProjectMappingResp(
  id: UUID,
  projectIdOrigin: UUID,
  projectIdLoaded: UUID,
  createdAt: Instant,
  updatedAt: Instant
)
