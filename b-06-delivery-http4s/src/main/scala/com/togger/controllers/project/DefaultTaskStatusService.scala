package com.togger.controllers.project

import com.togger.common.TgClock
import com.togger.task.TaskStatus

import java.util.UUID

class DefaultTaskStatusService(tgClock: TgClock) {

  def createDefaultTaskStatuses(projectId: UUID): Seq[TaskStatus] = {
    val now = tgClock.currentInstant
    Seq(
      ("📚 Backlog", "This has been completed"),
      ("📝 Todo", "This has been completed"),
      ("🏗️ In progress", "Things are moving"),
      ("👀 In review/Waiting", "This has been completed"),
      ("🧪 Testing", "This has been completed"),
      ("✅ Done", "This has been completed"),
      ("🔴 Blocked", "This has been completed"),
      ("🗑️ Won't do", "This has been completed")
    ).map { case (name, description) =>
      TaskStatus(
        id = UUID.randomUUID(),
        projectId = projectId,
        name = name,
        description = description,
        createdAt = now,
        updatedAt = now
      )
    }
  }

}
