package com.togger.controllers.logentry

import com.togger.dto.{LogEntryDto, LogEntryVersionDto}
import com.togger.logentry.{LogEntry, LogEntryVersion}

object LogEntryDtoMapper {

  def toDto(logEntry: LogEntry, version: LogEntryVersion): LogEntryDto =
    LogEntryDto(
      id = logEntry.id,
      taskId = logEntry.taskId,
      createdBy = logEntry.createdBy,
      createdAt = version.createdAt,
      latestVersion = version.version,
      textInternal = version.textInternal,
      textExternal = version.textExternal,
      status = version.status,
      updatedAt = version.updatedAt
    )

  def toVersionDto(version: LogEntryVersion): LogEntryVersionDto =
    LogEntryVersionDto(
      version = version.version,
      logEntryId = version.logEntryId,
      textInternal = version.textInternal,
      textExternal = version.textExternal,
      status = version.status,
      createdBy = version.createdBy,
      createdAt = version.createdAt,
      updatedAt = version.updatedAt
    )

}
