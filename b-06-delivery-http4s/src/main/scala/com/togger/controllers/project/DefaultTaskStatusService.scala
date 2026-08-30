package com.togger.controllers.project

import com.togger.common.TgClock
import com.togger.task.{DefaultTaskStatus, TaskStatus}

import java.util.UUID

class DefaultTaskStatusService(tgClock: TgClock) {

  def createDefaultTaskStatuses(projectId: UUID): Seq[TaskStatus] = {
    val now = tgClock.currentInstant
    DefaultTaskStatus.values.map { status =>
      TaskStatus(
        id = UUID.randomUUID(),
        projectId = projectId,
        name = status.statusName,
        description = status.description,
        createdAt = now,
        updatedAt = now
      )
    }
  }

}
