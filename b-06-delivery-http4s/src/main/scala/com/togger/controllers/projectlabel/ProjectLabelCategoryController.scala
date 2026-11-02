package com.togger.controllers.projectlabel

import com.togger.common.TgClock
import com.togger.common.TgJsonSuccess
import com.togger.controllers.common.PageRequestValidation
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
    findByIdEndpoint.serverLogic { _ => projectLabelCategoryId =>
      projectLabelCategoryService
        .findById(projectLabelCategoryId)
        .map { projectLabelCategory =>
          TgJsonSuccess.justBodyRight(
            FindProjectLabelCategoryResp(id = projectLabelCategory.id, name = projectLabelCategory.name)
          )
        }
    }

  val findAll =
    findAllEndpoint.serverLogic { _ => pageRequest =>
      PageRequestValidation.withPage(pageRequest) { page =>
        projectLabelCategoryService
          .findPaginated(page)
          .map(categoriesPage =>
            TgJsonSuccess.justBodyRight(
              categoriesPage.mapItem(projectLabelCategory =>
                FindProjectLabelCategoryResp(id = projectLabelCategory.id, name = projectLabelCategory.name)
              )
            )
          )
      }
    }

  val save = saveEndpoint.serverLogic { _ => dto =>
    val now = tgClock.currentInstant

    val projectLabelCategory = ProjectLabelCategory(
      id = UUID.randomUUID(),
      name = dto.name,
      createdAt = now,
      updatedAt = now
    )

    projectLabelCategoryService.createProjectLabelCategory(projectLabelCategory).map { projectLabelCategory =>
      TgJsonSuccess.justBodyRight(
        CreateProjectLabelCategoryResp(id = projectLabelCategory.id, name = projectLabelCategory.name)
      )
    }
  }

  val update = updateEndpoint.serverLogic { _ => dto =>
    val now = tgClock.currentInstant

    val projectLabelCategory = ProjectLabelCategory(
      id = dto.id,
      name = dto.name,
      createdAt = now,
      updatedAt = now
    )

    projectLabelCategoryService.updateProjectLabelCategory(projectLabelCategory).map { projectLabelCategory =>
      TgJsonSuccess.justBodyRight(
        FindProjectLabelCategoryResp(id = projectLabelCategory.id, name = projectLabelCategory.name)
      )
    }
  }

  val delete = deleteByIdEndpoint.serverLogic { _ => id =>
    projectLabelCategoryService
      .deleteById(id)
      .map(_ => TgJsonSuccess.justMessageRight("Project label category deleted successfully"))
  }

}
