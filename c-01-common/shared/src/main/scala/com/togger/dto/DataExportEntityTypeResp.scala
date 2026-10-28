package com.togger.dto

import enumeratum._

sealed trait DataExportEntityTypeResp extends EnumEntry

object DataExportEntityTypeResp extends Enum[DataExportEntityTypeResp] {
  override def values: IndexedSeq[DataExportEntityTypeResp] = findValues

  case object Project extends DataExportEntityTypeResp
  case object Members extends DataExportEntityTypeResp
  case object TaskStatus extends DataExportEntityTypeResp
  case object TaskLabel extends DataExportEntityTypeResp
  case object ProjectLabel extends DataExportEntityTypeResp
  case object Milestone extends DataExportEntityTypeResp
  case object Task extends DataExportEntityTypeResp
  case object LogEntry extends DataExportEntityTypeResp
  case object TaskTemplate extends DataExportEntityTypeResp
  case object Timebox extends DataExportEntityTypeResp
  case object TgPromptTemplate extends DataExportEntityTypeResp
}
