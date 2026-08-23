package com.togger.controllers.projectmapping

import com.togger.common.{TgClock, TgJsonSuccess}
import com.togger.dto.projectmapping.ProjectMappingResp
import com.togger.projectmapping.{ProjectMapping, ProjectMappingEndpoints, ProjectMappingService}

import java.util.UUID

class ProjectMappingController(
  projectMappingEndpoints: ProjectMappingEndpoints,
  projectMappingService: ProjectMappingService,
  tgClock: TgClock
) {

  import projectMappingEndpoints._

  private def toResp(projectMapping: ProjectMapping): ProjectMappingResp =
    ProjectMappingResp(
      id = projectMapping.id,
      projectIdDump = projectMapping.projectIdDump,
      projectIdDb = projectMapping.projectIdDb,
      createdAt = projectMapping.createdAt,
      updatedAt = projectMapping.updatedAt
    )

  val findById =
    findByIdEndpoint.serverLogic { _ => id =>
      projectMappingService
        .findById(id)
        .map(projectMapping => TgJsonSuccess.justBodyRight(toResp(projectMapping)))
    }

  val save = saveEndpoint.serverLogic { _ => dto =>
    val now = tgClock.currentInstant

    val projectMapping = ProjectMapping(
      id = UUID.randomUUID(),
      projectIdDump = dto.projectIdDump,
      projectIdDb = dto.projectIdDb,
      createdAt = now,
      updatedAt = now
    )

    projectMappingService
      .create(projectMapping)
      .map(saved => TgJsonSuccess.justBodyRight(toResp(saved)))
  }

  val update = updateEndpoint.serverLogic { _ => dto =>
    val now = tgClock.currentInstant

    val projectMapping = ProjectMapping(
      id = dto.id,
      projectIdDump = dto.projectIdDump,
      projectIdDb = dto.projectIdDb,
      createdAt = now,
      updatedAt = now
    )

    projectMappingService
      .update(projectMapping)
      .map(updated => TgJsonSuccess.justBodyRight(toResp(updated)))
  }

  val delete = deleteByIdEndpoint.serverLogic { _ => id =>
    projectMappingService
      .deleteById(id)
      .map(_ => TgJsonSuccess.justMessageRight("Project mapping deleted successfully"))
  }

  val findByProjectIdDb =
    findByProjectIdDbEndpoint.serverLogic { _ => projectIdDb =>
      projectMappingService
        .findByProjectIdDb(projectIdDb)
        .map(mappings => TgJsonSuccess.justBodyRight(mappings.map(toResp)))
    }

}
