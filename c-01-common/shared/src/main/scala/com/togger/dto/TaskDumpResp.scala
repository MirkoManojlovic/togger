package com.togger.dto

import java.util.UUID

case class TaskDumpResp(
  projectId: UUID,
  dumpPath: String,
  taskCount: Int
)
