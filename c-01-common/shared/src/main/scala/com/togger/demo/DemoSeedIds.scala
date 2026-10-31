package com.togger.demo

import java.util.UUID

/** Deterministic ids for seeded rows, one `group` per kind of entity, so that seeders can reference each other. */
object DemoSeedIds {
  val TaskStatus = 3
  val TaskLabel = 4
  val TaskTemplate = 5
  val ProjectLabelCategory = 6
  val ProjectLabel = 7
  val Milestone = 8
  val MilestoneDate = 9
  val MilestoneSummary = 10
  val PromptTemplate = 11
  val AiAction = 12
  val Task = 13
  val TaskVersion = 14
  val Link = 15
  val LinkVersion = 16
  val LogEntry = 17
  val LogEntryVersion = 18
  val Timebox = 19
  val TimeboxVersion = 20
  val ProjectMapping = 21
  val DataSyncConfig = 22
  val AiActionDefault = 23

  def id(group: Int, number: Int): UUID =
    new UUID(0x0000000000007000L, 0x8000000000000000L | (group.toLong << 16) | number.toLong)
}
