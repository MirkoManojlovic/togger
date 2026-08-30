package com.togger.controllers.projectmapping

import com.togger.common.{TgClock, TgJsonSuccess}
import com.togger.dto.projectmapping.ProjectMappingResp
import com.togger.projectmapping.{ProjectMapping, ProjectMappingEndpoints, ProjectMappingService}

class ProjectMappingController(
  projectMappingEndpoints: ProjectMappingEndpoints,
  projectMappingService: ProjectMappingService,
  tgClock: TgClock
) {

  import projectMappingEndpoints._

  private def toResp(projectMapping: ProjectMapping): ProjectMappingResp =
    ProjectMappingResp(
      projectIdDb = projectMapping.projectIdDb,
      projectIdDump = projectMapping.projectIdDump,
      createdAt = projectMapping.createdAt,
      updatedAt = projectMapping.updatedAt
    )

  val save = saveEndpoint.serverLogic { _ => dto =>
    val now = tgClock.currentInstant

    val projectMapping = ProjectMapping(
      projectIdDb = dto.projectIdDb,
      projectIdDump = dto.projectIdDump,
      createdAt = now,
      updatedAt = now
    )

    projectMappingService
      .create(projectMapping)
      .map(saved => TgJsonSuccess.justBodyRight(toResp(saved)))
  }

  val findByProjectIdDb =
    findByProjectIdDbEndpoint.serverLogic { _ => projectIdDb =>
      projectMappingService
        .findByProjectIdDb(projectIdDb)
        .map(mappings => TgJsonSuccess.justBodyRight(mappings.map(toResp)))
    }

  val findByProjectIdDump =
    findByProjectIdDumpEndpoint.serverLogic { _ => projectIdDump =>
      projectMappingService
        .findByProjectIdDump(projectIdDump)
        .map(mappings => TgJsonSuccess.justBodyRight(mappings.map(toResp)))
    }

  val delete = deleteEndpoint.serverLogic { _ =>
    { case (projectIdDb, projectIdDump) =>
      projectMappingService
        .deleteByIds(projectIdDb, projectIdDump)
        .map(_ => TgJsonSuccess.justMessageRight("Project mapping deleted successfully"))
    }
  }

}
