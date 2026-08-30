package com.togger.task

import enumeratum._

sealed abstract class DefaultTaskStatus(val i: Int, val statusName: String, val description: String) extends EnumEntry

object DefaultTaskStatus extends Enum[DefaultTaskStatus] {

  case object InProgress extends DefaultTaskStatus(0, "🏗️ In progress", "Things are moving")
  case object Testing extends DefaultTaskStatus(1, "🧪 Testing", "This has been completed")
  case object InReviewWaiting extends DefaultTaskStatus(2, "👀 In review/Waiting", "This has been completed")
  case object Blocked extends DefaultTaskStatus(3, "🔴 Blocked", "This has been completed")
  case object Todo extends DefaultTaskStatus(4, "📝 Todo", "This has been completed")
  case object Backlog extends DefaultTaskStatus(5, "📚 Backlog", "This has been completed")
  case object Done extends DefaultTaskStatus(6, "✅ Done", "This has been completed")
  case object WontDo extends DefaultTaskStatus(7, "🗑️ Won't do", "This has been completed")
  case object Archived extends DefaultTaskStatus(8, "📦 Archived", "This has been archived")

  override def values: IndexedSeq[DefaultTaskStatus] = findValues

}
