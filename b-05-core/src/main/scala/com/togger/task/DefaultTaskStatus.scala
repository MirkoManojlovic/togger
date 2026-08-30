package com.togger.task

import enumeratum._

sealed abstract class DefaultTaskStatus(val statusName: String, val description: String) extends EnumEntry

object DefaultTaskStatus extends Enum[DefaultTaskStatus] {

  case object InProgress extends DefaultTaskStatus("🏗️ In progress", "Things are moving")
  case object Testing extends DefaultTaskStatus("🧪 Testing", "This has been completed")
  case object InReviewWaiting extends DefaultTaskStatus("👀 In review/Waiting", "This has been completed")
  case object Blocked extends DefaultTaskStatus("🔴 Blocked", "This has been completed")
  case object Todo extends DefaultTaskStatus("📝 Todo", "This has been completed")
  case object Backlog extends DefaultTaskStatus("📚 Backlog", "This has been completed")
  case object Done extends DefaultTaskStatus("✅ Done", "This has been completed")
  case object WontDo extends DefaultTaskStatus("🗑️ Won't do", "This has been completed")
  case object Archived extends DefaultTaskStatus("📦 Archived", "This has been archived")

  override def values: IndexedSeq[DefaultTaskStatus] = findValues

}
