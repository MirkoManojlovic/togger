package com.togger.chart

import enumeratum._

/** The colors a chartable thing can have, one per hue of the badge palette. `light` is for a light background and
  * `dark` for a dark one, both as `#RRGGBB`.
  */
sealed abstract class ChartColor(val light: String, val dark: String) extends EnumEntry

object ChartColor extends Enum[ChartColor] {

  case object Blue extends ChartColor("#60A5FA", "#3B82F6")
  case object Green extends ChartColor("#4ADE80", "#22C55E")
  case object Red extends ChartColor("#F87171", "#EF4444")
  case object Yellow extends ChartColor("#FACC15", "#EAB308")
  case object Orange extends ChartColor("#FB923C", "#F97316")
  case object Lime extends ChartColor("#A3E635", "#84CC16")
  case object Purple extends ChartColor("#C084FC", "#A855F7")
  case object Cyan extends ChartColor("#22D3EE", "#06B6D4")
  case object Gray extends ChartColor("#9CA3AF", "#6B7280")
  case object Dark extends ChartColor("#374151", "#1F2937")

  override def values: IndexedSeq[ChartColor] = findValues

}
