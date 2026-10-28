package com.togger.ai

import enumeratum.values.{IntEnum, IntEnumEntry}

sealed abstract class TgAiEndpoints(val value: Int, val endpointName: String) extends IntEnumEntry

object TgAiEndpoints extends IntEnum[TgAiEndpoints] {

  case object MilestoneSummary extends TgAiEndpoints(0, "milestone-summary")

  override def values: IndexedSeq[TgAiEndpoints] = findValues

}
