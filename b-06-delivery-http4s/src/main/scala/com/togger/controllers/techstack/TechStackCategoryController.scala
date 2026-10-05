package com.togger.controllers.techstack

import com.togger.common.{TgClock, TgJsonSuccess}
import com.togger.dto._
import com.togger.techstack._

import java.util.UUID

class TechStackCategoryController(
  techStackCategoryService: TechStackCategoryService,
  techStackCategoryEndpoints: TechStackCategoryEndpoints,
  tgClock: TgClock
) {

  import techStackCategoryEndpoints._

  val findById =
    findByIdEndpoint.serverLogic { auth => techStackCategoryId =>
      techStackCategoryService
        .findById(techStackCategoryId)
        .map { techStackCategory =>
          TgJsonSuccess.justBodyRight(
            FindTechStackCategoryResp(id = techStackCategory.id, name = techStackCategory.name)
          )
        }
    }

  val findAll =
    findAllEndpoint.serverLogic { auth => _ =>
      techStackCategoryService.findAll
        .map(techStackCategories =>
          TgJsonSuccess.justBodyRight(
            techStackCategories.map(techStackCategory =>
              FindTechStackCategoryResp(id = techStackCategory.id, name = techStackCategory.name)
            )
          )
        )
    }

  val save = saveEndpoint.serverLogic { auth => dto =>
    val now = tgClock.currentInstant

    val techStackCategory = TechStackCategory(
      id = UUID.randomUUID(),
      name = dto.name,
      createdAt = now,
      updatedAt = now
    )

    techStackCategoryService.createTechStackCategory(techStackCategory).map { techStackCategory =>
      TgJsonSuccess.justBodyRight(CreateTechStackCategoryResp(id = techStackCategory.id, name = techStackCategory.name))
    }
  }

  val update = updateEndpoint.serverLogic { auth => dto =>
    val now = tgClock.currentInstant

    val techStackCategory = TechStackCategory(
      id = dto.id,
      name = dto.name,
      createdAt = now,
      updatedAt = now
    )

    techStackCategoryService.updateTechStackCategory(techStackCategory).map { techStackCategory =>
      TgJsonSuccess.justBodyRight(FindTechStackCategoryResp(id = techStackCategory.id, name = techStackCategory.name))
    }
  }

  val delete = deleteByIdEndpoint.serverLogic { auth => id =>
    techStackCategoryService
      .deleteById(id)
      .map(_ => TgJsonSuccess.justMessageRight("Tech stack category deleted successfully"))
  }

}
