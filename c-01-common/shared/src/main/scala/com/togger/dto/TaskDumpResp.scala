package com.togger.dto

import java.util.UUID

case class TaskDumpPathResultResp(
  dumpPath: String,
  error: Option[String]
) {
  lazy val success: Boolean = error.isEmpty
}

object TaskDumpPathResultResp {
  def success(dumpPath: String) =
    TaskDumpPathResultResp(dumpPath = dumpPath, error = None)

  def error(dumpPath: String, error: String) =
    TaskDumpPathResultResp(dumpPath = dumpPath, error = Some(error))
}
case class TaskDumpResp(
  projectId: UUID,
  taskCount: Int,
  results: List[TaskDumpPathResultResp]
)
