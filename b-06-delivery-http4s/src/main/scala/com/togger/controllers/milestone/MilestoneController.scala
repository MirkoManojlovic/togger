package com.togger.controllers.milestone

import com.togger.common.TgError._
import com.togger.common.TgJsonSuccess
import com.togger.controllers.milestone.MilestoneDtoMapper.{toDto, toVersionDto}
import com.togger.dto.{FindMilestoneResp, MilestoneVersionDto}
import com.togger.milestone.{Milestone, MilestoneEndpoints, MilestoneService, MilestoneVersion}

import java.util.UUID

class MilestoneController(
  milestoneEndpoints: MilestoneEndpoints,
  milestoneService: MilestoneService
) {

  val save = milestoneEndpoints.saveEndpoint.serverLogic { auth => dto =>
    val now = java.time.Instant.now()
    val milestone = Milestone(
      id = UUID.randomUUID(),
      projectId = dto.projectId,
      createdAt = now
    )
    val firstVersion = MilestoneVersion(
      version = UUID.randomUUID(),
      milestoneId = milestone.id,
      name = dto.name,
      description = dto.description,
      createdAt = now,
      updatedAt = now
    )

    milestoneService
      .createMilestone(milestone, firstVersion)
      .map(result => TgJsonSuccess.justBodyRight(toDto(result.milestone, result.version)))
  }

  val findById = milestoneEndpoints.findByIdEndpoint.serverLogic { auth => id =>
    milestoneService
      .getCurrent(id)
      .map {
        case Right(result) => TgJsonSuccess.justBodyRight(toDto(result.milestone, result.version))
        case Left(error)   => error.toBadRequestLeft[FindMilestoneResp]
      }
  }

  val findByProjectId = milestoneEndpoints.findByProjectIdEndpoint.serverLogic { auth => projectId =>
    milestoneService
      .findByProjectId(projectId)
      .map(milestonesWithLatestVersion =>
        TgJsonSuccess.justBodyRight(milestonesWithLatestVersion.map(m => toDto(m.milestone, m.versions.head)))
      )
  }

  val update = milestoneEndpoints.updateEndpoint.serverLogic { auth => dto =>
    val now = java.time.Instant.now()
    val newVersion = MilestoneVersion(
      version = UUID.randomUUID(),
      milestoneId = dto.id,
      name = dto.name,
      description = dto.description,
      createdAt = now,
      updatedAt = now
    )

    milestoneService
      .saveNewVersion(dto.id, newVersion)
      .map {
        case Right(result) => TgJsonSuccess.justBodyRight(toDto(result.milestone, result.version))
        case Left(error)   => error.toBadRequestLeft[FindMilestoneResp]
      }
  }

  val delete = milestoneEndpoints.deleteByIdEndpoint.serverLogic { auth => id =>
    milestoneService
      .deleteMilestone(id)
      .map(_ => TgJsonSuccess.justMessageRight("Milestone deleted successfully"))
  }

  val listVersions = milestoneEndpoints.listVersionsEndpoint.serverLogic { auth => milestoneId =>
    milestoneService
      .listVersions(milestoneId)
      .map(versions => TgJsonSuccess.justBodyRight(versions.map(toVersionDto)))
  }

  val updateVersion = milestoneEndpoints.updateVersionEndpoint.serverLogic { auth => dto =>
    milestoneService
      .updateVersionContent(dto.version, dto.name, dto.description)
      .map {
        case Right(version) => TgJsonSuccess.justBodyRight(toVersionDto(version))
        case Left(error)    => error.toBadRequestLeft[MilestoneVersionDto]
      }
  }

  val deleteVersion = milestoneEndpoints.deleteVersionEndpoint.serverLogic { auth => version =>
    milestoneService
      .deleteVersion(version)
      .map {
        case Right(_)    => TgJsonSuccess.justMessageRight("Milestone version deleted successfully")
        case Left(error) => error.toBadRequestLeft[TgJsonSuccess.TgEmptyBody]
      }
  }
}
