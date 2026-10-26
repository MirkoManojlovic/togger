package com.togger.controllers.logentry

import cats.effect.IO
import com.togger.account.AccountNameLookup
import com.togger.common.TgError._
import com.togger.common.TgJsonSuccess
import com.togger.controllers.common.PageRequestValidation
import com.togger.controllers.logentry.LogEntryDtoMapper.{toDto, toVersionDto}
import com.togger.dto.{LogEntryDto, LogEntryVersionDto}
import com.togger.logentry.{LogEntry, LogEntryEndpoints, LogEntryService, LogEntryVersion}

import java.util.UUID

class LogEntryController(
  logEntryEndpoints: LogEntryEndpoints,
  logEntryService: LogEntryService,
  accountNameLookup: AccountNameLookup
) {

  val save = logEntryEndpoints.saveEndpoint.serverLogic { auth => dto =>
    val logEntry = LogEntry(
      id = UUID.randomUUID(),
      taskId = dto.taskId,
      createdBy = dto.createdBy,
      createdAt = dto.createdAt
    )
    val firstVersion = LogEntryVersion(
      version = UUID.randomUUID(),
      logEntryId = logEntry.id,
      textInternal = dto.textInternal,
      textExternal = dto.textExternal,
      status = dto.status,
      createdBy = dto.createdBy,
      createdAt = dto.createdAt,
      updatedAt = dto.updatedAt
    )

    logEntryService
      .createLogEntryOncePerDay(logEntry, firstVersion)
      .flatMap {
        case Right(result) =>
          accountNameLookup
            .namesByIds(Set(result.logEntry.createdBy))
            .map(names => TgJsonSuccess.justBodyRight(toDto(result.logEntry, result.version, names)))
        case Left(error) => IO.pure(error.toBadRequestLeft[LogEntryDto])
      }
  }

  val findByTaskId = logEntryEndpoints.findByTaskId.serverLogic { auth => request =>
    val (taskId, pageRequest) = request
    PageRequestValidation.withPage(pageRequest) { page =>
      logEntryService
        .findPaginatedByTaskId(taskId, page)
        .flatMap(entriesPage =>
          accountNameLookup
            .namesByIds(entriesPage.items.map(_.logEntry.createdBy).toSet)
            .map(names =>
              TgJsonSuccess.justBodyRight(entriesPage.mapItem(entry => toDto(entry.logEntry, entry.versions.head, names)))
            )
        )
    }
  }

  val findByProjectId = logEntryEndpoints.findByProjectIdEndpoint.serverLogic { auth => request =>
    val (projectId, filter, pageRequest) = request
    PageRequestValidation.withPage(pageRequest) { page =>
      logEntryService
        .findPaginatedByProjectId(projectId, filter, page)
        .flatMap(entriesPage =>
          accountNameLookup
            .namesByIds(entriesPage.items.map(_.logEntry.createdBy).toSet)
            .map(names =>
              TgJsonSuccess.justBodyRight(entriesPage.mapItem(entry => toDto(entry.logEntry, entry.latestVersion, names)))
            )
        )
    }
  }

  val updateLogEntry = logEntryEndpoints.updateEndpoint.serverLogic { auth => dto =>
    val newVersion = LogEntryVersion(
      version = UUID.randomUUID(),
      logEntryId = dto.id,
      textInternal = dto.textInternal,
      textExternal = dto.textExternal,
      status = dto.status,
      createdBy = dto.createdBy,
      createdAt = dto.createdAt,
      updatedAt = dto.updatedAt
    )

    logEntryService
      .saveNewVersion(dto.id, newVersion)
      .flatMap {
        case Right(result) =>
          accountNameLookup
            .namesByIds(Set(result.logEntry.createdBy))
            .map(names => TgJsonSuccess.justBodyRight(toDto(result.logEntry, result.version, names)))
        case Left(error) => IO.pure(error.toBadRequestLeft[LogEntryDto])
      }
  }

  val listVersions = logEntryEndpoints.listVersionsEndpoint.serverLogic { auth => request =>
    val (logEntryId, pageRequest) = request
    PageRequestValidation.withPage(pageRequest) { page =>
      logEntryService
        .listVersions(logEntryId, page)
        .map(versionsPage => TgJsonSuccess.justBodyRight(versionsPage.mapItem(toVersionDto)))
    }
  }

  val updateVersion = logEntryEndpoints.updateVersionEndpoint.serverLogic { auth => dto =>
    logEntryService
      .updateVersionContent(dto.version, dto.textInternal, dto.textExternal, dto.status)
      .map {
        case Right(version) => TgJsonSuccess.justBodyRight(toVersionDto(version))
        case Left(error)    => error.toBadRequestLeft[LogEntryVersionDto]
      }
  }

  val deleteVersion = logEntryEndpoints.deleteVersionEndpoint.serverLogic { auth => version =>
    logEntryService
      .deleteVersion(version)
      .map {
        case Right(_)    => TgJsonSuccess.justMessageRight("Log entry version deleted successfully")
        case Left(error) => error.toBadRequestLeft[TgJsonSuccess.TgEmptyBody]
      }
  }
}
