package com.togger.controllers.project

import com.togger.common.TgClock
import com.togger.task.{DefaultTaskLabel, TaskLabel}

import java.util.UUID

class DefaultTaskLabelService(tgClock: TgClock) {

  def createDefaultTaskLabels(projectId: UUID): Seq[TaskLabel] = {
    val now = tgClock.currentInstant
    DefaultTaskLabel.values.map { label =>
      TaskLabel(
        id = UUID.randomUUID(),
        projectId = projectId,
        name = label.labelName,
        description = label.description,
        createdAt = now,
        updatedAt = now
      )
    }
  }

}
