package com.togger.controllers.logentry

import cats.effect.IO
import com.togger.common.{TgClock, TgJsonSuccess}
import com.togger.dto.LogEntryDto
import com.togger.logentry.{LogEntry, LogEntryEndpoints, LogEntryRepo}
import doobie.Transactor
import doobie.implicits._

class LogEntryController(
  logEntryEndpoints: LogEntryEndpoints,
  logEntryRepo: LogEntryRepo,
  tgClock: TgClock,
  transactor: Transactor[IO]
) {

  val save = logEntryEndpoints.saveEndpoint.serverLogic { auth => dto =>
    val logEntry = LogEntry(
      id = -1,
      textInternal = dto.textInternal,
      textExternal = dto.textExternal,
      status = dto.status,
      taskId = dto.taskId,
      createdBy = dto.createdBy,
      createdAt = dto.createdAt,
      updatedAt = dto.updatedAt
    )

    logEntryRepo
      .insert(logEntry)
      .map(_ => TgJsonSuccess.justBodyRight(dto))
      .transact(transactor)

  }

  val findByTaskId = logEntryEndpoints.findByTaskId.serverLogic { auth => ticketId =>
    logEntryRepo
      .findByTaskId(ticketId)
      .map(logEntries =>
        logEntries.map(logEntry =>
          LogEntryDto(
            id = logEntry.id,
            textInternal = logEntry.textInternal,
            textExternal = logEntry.textExternal,
            status = logEntry.status,
            taskId = logEntry.taskId,
            createdBy = logEntry.createdBy,
            createdAt = logEntry.createdAt,
            updatedAt = logEntry.updatedAt
          )
        )
      )
      .map(dtos => TgJsonSuccess.justBodyRight(dtos))
      .transact(transactor)
  }

  val updateLogEntry = logEntryEndpoints.updateEndpoint.serverLogic { auth => dto =>
    val logEntry = LogEntry(
      id = dto.id,
      textInternal = dto.textInternal,
      textExternal = dto.textExternal,
      status = dto.status,
      taskId = dto.taskId,
      createdBy = dto.createdBy,
      createdAt = dto.createdAt,
      updatedAt = dto.updatedAt
    )

    logEntryRepo
      .update(logEntry, dto.updatedAt)
      .map(_ => TgJsonSuccess.justBodyRight(dto))
      .transact(transactor)
  }
}
