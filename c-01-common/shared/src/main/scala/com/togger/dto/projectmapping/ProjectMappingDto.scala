package com.togger.dto.projectmapping

import java.time.Instant
import java.util.UUID

case class CreateProjectMappingReq(projectIdDump: UUID, projectIdDb: UUID)

case class UpdateProjectMappingReq(id: UUID, projectIdDump: UUID, projectIdDb: UUID)

case class ProjectMappingResp(
  id: UUID,
  projectIdDump: UUID,
  projectIdDb: UUID,
  createdAt: Instant,
  updatedAt: Instant
)
