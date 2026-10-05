package com.togger.controllers.techstack

import com.togger.common.{TgClock, TgJsonSuccess}
import com.togger.dto._
import com.togger.techstack._

import java.util.UUID

class TechStackController(
  techStackService: TechStackService,
  techStackEndpoints: TechStackEndpoints,
  tgClock: TgClock
) {

  import techStackEndpoints._

  val findById =
    findByIdEndpoint.serverLogic { auth => techStackId =>
      techStackService
        .findById(techStackId)
        .map { techStack =>
          TgJsonSuccess.justBodyRight(
            FindTechStackResp(
              id = techStack.id,
              name = techStack.name,
              description = techStack.description,
              categoryId = techStack.categoryId
            )
          )
        }
    }

  val findAll =
    findAllEndpoint.serverLogic { auth => _ =>
      techStackService.findAll
        .map(techStacks =>
          TgJsonSuccess.justBodyRight(
            techStacks
              .map(techStack =>
                FindTechStackResp(
                  id = techStack.id,
                  name = techStack.name,
                  description = techStack.description,
                  categoryId = techStack.categoryId
                )
              )
          )
        )
    }

  val save = saveEndpoint.serverLogic { auth => dto =>
    val now = tgClock.currentInstant

    val techStack = TechStack(
      id = UUID.randomUUID(),
      name = dto.name,
      description = dto.description,
      categoryId = dto.categoryId,
      createdAt = now,
      updatedAt = now
    )

    techStackService.createTechStack(techStack).map { techStack =>
      val respDto = CreateTechStackResp(
        id = techStack.id,
        name = techStack.name,
        description = techStack.description,
        categoryId = techStack.categoryId
      )
      TgJsonSuccess.justBodyRight(respDto)
    }
  }

  val update = updateEndpoint.serverLogic { auth => dto =>
    val now = tgClock.currentInstant

    val techStack = TechStack(
      id = dto.id,
      name = dto.name,
      description = dto.description,
      categoryId = dto.categoryId,
      createdAt = now,
      updatedAt = now
    )

    techStackService.updateTechStack(techStack).map { techStack =>
      TgJsonSuccess.justBodyRight(
        FindTechStackResp(
          id = techStack.id,
          name = techStack.name,
          description = techStack.description,
          categoryId = techStack.categoryId
        )
      )
    }
  }

  val delete = deleteByIdEndpoint.serverLogic { auth => id =>
    techStackService
      .deleteById(id)
      .map(_ => TgJsonSuccess.justMessageRight("Tech stack deleted successfully"))
  }

}
