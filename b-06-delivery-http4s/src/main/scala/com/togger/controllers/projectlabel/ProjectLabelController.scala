package com.togger.controllers.projectlabel

import com.togger.common.{TgClock, TgJsonSuccess}
import com.togger.dto._
import com.togger.projectlabel._

import java.util.UUID

class ProjectLabelController(
  projectLabelService: ProjectLabelService,
  projectLabelEndpoints: ProjectLabelEndpoints,
  tgClock: TgClock
) {

  import projectLabelEndpoints._

  val findById =
    findByIdEndpoint.serverLogic { auth => projectLabelId =>
      projectLabelService
        .findById(projectLabelId)
        .map { projectLabel =>
          TgJsonSuccess.justBodyRight(
            FindProjectLabelResp(
              id = projectLabel.id,
              name = projectLabel.name,
              description = projectLabel.description,
              categoryId = projectLabel.categoryId
            )
          )
        }
    }

  val findAll =
    findAllEndpoint.serverLogic { auth => _ =>
      projectLabelService.findAll
        .map(projectLabels =>
          TgJsonSuccess.justBodyRight(
            projectLabels
              .map(projectLabel =>
                FindProjectLabelResp(
                  id = projectLabel.id,
                  name = projectLabel.name,
                  description = projectLabel.description,
                  categoryId = projectLabel.categoryId
                )
              )
          )
        )
    }

  val save = saveEndpoint.serverLogic { auth => dto =>
    val now = tgClock.currentInstant

    val projectLabel = ProjectLabel(
      id = UUID.randomUUID(),
      name = dto.name,
      description = dto.description,
      categoryId = dto.categoryId,
      createdAt = now,
      updatedAt = now
    )

    projectLabelService.createProjectLabel(projectLabel).map { projectLabel =>
      val respDto = CreateProjectLabelResp(
        id = projectLabel.id,
        name = projectLabel.name,
        description = projectLabel.description,
        categoryId = projectLabel.categoryId
      )
      TgJsonSuccess.justBodyRight(respDto)
    }
  }

  val update = updateEndpoint.serverLogic { auth => dto =>
    val now = tgClock.currentInstant

    val projectLabel = ProjectLabel(
      id = dto.id,
      name = dto.name,
      description = dto.description,
      categoryId = dto.categoryId,
      createdAt = now,
      updatedAt = now
    )

    projectLabelService.updateProjectLabel(projectLabel).map { projectLabel =>
      TgJsonSuccess.justBodyRight(
        FindProjectLabelResp(
          id = projectLabel.id,
          name = projectLabel.name,
          description = projectLabel.description,
          categoryId = projectLabel.categoryId
        )
      )
    }
  }

  val delete = deleteByIdEndpoint.serverLogic { auth => id =>
    projectLabelService
      .deleteById(id)
      .map(_ => TgJsonSuccess.justMessageRight("Project label deleted successfully"))
  }

}
