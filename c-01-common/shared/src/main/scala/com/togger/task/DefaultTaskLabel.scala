package com.togger.task

import enumeratum._

sealed abstract class DefaultTaskLabel(val i: Int, val labelName: String, val description: String) extends EnumEntry

object DefaultTaskLabel extends Enum[DefaultTaskLabel] {

  case object Feature extends DefaultTaskLabel(0, "🆕 feature", "New functionality")
  case object Bug extends DefaultTaskLabel(1, "🐞 bug", "Something isn't working")
  case object Refactor extends DefaultTaskLabel(2, "🧱 refactor", "Code change with no behavior change")
  case object Test extends DefaultTaskLabel(3, "🧪 test", "Adding or updating tests")
  case object Docs extends DefaultTaskLabel(4, "📑 docs", "Documentation only changes")
  case object Chore extends DefaultTaskLabel(5, "🧹 chore", "Maintenance, tooling or build work")

  override def values: IndexedSeq[DefaultTaskLabel] = findValues

}
