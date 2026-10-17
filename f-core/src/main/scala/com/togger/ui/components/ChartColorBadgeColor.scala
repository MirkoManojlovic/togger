package com.togger.ui.components

import com.togger.chart.{ChartColor, Chartable}

/** Maps a [[ChartColor]] onto the [[TgBadgeColor]] of the same hue, so a badge and a chart show the same thing in the
  * same color.
  */
object ChartColorBadgeColor {

  def of(color: ChartColor): TgBadgeColor = color match {
    case ChartColor.Blue   => TgBadgeColor.Blue
    case ChartColor.Green  => TgBadgeColor.Green
    case ChartColor.Red    => TgBadgeColor.Red
    case ChartColor.Yellow => TgBadgeColor.Yellow
    case ChartColor.Orange => TgBadgeColor.Orange
    case ChartColor.Lime   => TgBadgeColor.Lime
    case ChartColor.Purple => TgBadgeColor.Purple
    case ChartColor.Cyan   => TgBadgeColor.Cyan
    case ChartColor.Gray   => TgBadgeColor.Gray
    case ChartColor.Dark   => TgBadgeColor.Dark
  }

  def of(chartable: Chartable): TgBadgeColor = of(chartable.chartColor)

}
