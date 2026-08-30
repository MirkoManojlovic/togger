package com.togger.dto.projectmapping

import java.time.Instant
import java.util.UUID

case class CreateProjectMappingReq(projectIdDb: UUID, projectIdDump: UUID)

case class ProjectMappingResp(projectIdDb: UUID, projectIdDump: UUID, createdAt: Instant, updatedAt: Instant)
