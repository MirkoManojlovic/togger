package com.togger.chart

/** Something that can be drawn in a chart. The same thing always gets the same label and color, wherever it is drawn. */
trait Chartable {

  /** Stable id to tell the series of a chart apart, e.g. `task-status.blocked`. */
  def key: String

  def label: String

  def chartColor: ChartColor

  /** Hex for a light background. */
  def color: String = chartColor.light

  /** Hex for a dark background. */
  def darkColor: String = chartColor.dark

}

object Chartable {

  /** For the things that match no known [[Chartable]], e.g. a status a user made up. */
  final case class Unknown(key: String, label: String) extends Chartable {
    val chartColor: ChartColor = ChartColor.Gray
  }

}
