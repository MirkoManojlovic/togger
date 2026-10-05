package com.togger.controllers.projectlabel

import com.togger.common.{TgClock, TgJsonSuccess}
import com.togger.dto._
import com.togger.projectlabel._

import java.util.UUID

class ProjectLabelCategoryController(
  projectLabelCategoryService: ProjectLabelCategoryService,
  projectLabelCategoryEndpoints: ProjectLabelCategoryEndpoints,
  tgClock: TgClock
) {

  import projectLabelCategoryEndpoints._

  val findById =
    findByIdEndpoint.serverLogic { auth => projectLabelCategoryId =>
      projectLabelCategoryService
        .findById(projectLabelCategoryId)
        .map { projectLabelCategory =>
          TgJsonSuccess.justBodyRight(
            FindProjectLabelCategoryResp(id = projectLabelCategory.id, name = projectLabelCategory.name)
          )
        }
    }

  val findAll =
    findAllEndpoint.serverLogic { auth => _ =>
      projectLabelCategoryService.findAll
        .map(projectLabelCategories =>
          TgJsonSuccess.justBodyRight(
            projectLabelCategories.map(projectLabelCategory =>
              FindProjectLabelCategoryResp(id = projectLabelCategory.id, name = projectLabelCategory.name)
            )
          )
        )
    }

  val save = saveEndpoint.serverLogic { auth => dto =>
    val now = tgClock.currentInstant

    val projectLabelCategory = ProjectLabelCategory(
      id = UUID.randomUUID(),
      name = dto.name,
      createdAt = now,
      updatedAt = now
    )

    projectLabelCategoryService.createProjectLabelCategory(projectLabelCategory).map { projectLabelCategory =>
      TgJsonSuccess.justBodyRight(CreateProjectLabelCategoryResp(id = projectLabelCategory.id, name = projectLabelCategory.name))
    }
  }

  val update = updateEndpoint.serverLogic { auth => dto =>
    val now = tgClock.currentInstant

    val projectLabelCategory = ProjectLabelCategory(
      id = dto.id,
      name = dto.name,
      createdAt = now,
      updatedAt = now
    )

    projectLabelCategoryService.updateProjectLabelCategory(projectLabelCategory).map { projectLabelCategory =>
      TgJsonSuccess.justBodyRight(FindProjectLabelCategoryResp(id = projectLabelCategory.id, name = projectLabelCategory.name))
    }
  }

  val delete = deleteByIdEndpoint.serverLogic { auth => id =>
    projectLabelCategoryService
      .deleteById(id)
      .map(_ => TgJsonSuccess.justMessageRight("Project label category deleted successfully"))
  }

}
