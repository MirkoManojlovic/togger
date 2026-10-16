package com.togger.ui.apexchart

sealed trait ApexChartType {
  def jsValue: String
}

object ApexChartType {
  case object Area extends ApexChartType { val jsValue = "area" }
  case object Donut extends ApexChartType { val jsValue = "donut" }
  case object Pie extends ApexChartType { val jsValue = "pie" }
}

case class ApexNamedSeries(name: String, data: Seq[Int])

sealed trait ApexSeries

object ApexSeries {
  case class Axis(series: Seq[ApexNamedSeries], categories: Seq[String]) extends ApexSeries
  case class Slices(values: Seq[Int], labels: Seq[String]) extends ApexSeries
}

case class ApexStroke(smooth: Boolean, width: Int)

sealed trait ApexLegendPosition {
  def jsValue: String
}

object ApexLegendPosition {
  case object Bottom extends ApexLegendPosition { val jsValue = "bottom" }
}

/** @param colors
  *   the series colors; the theme's own when empty
  * @param lightColors
  *   the series colors in light mode, where `colors` (made for a dark background) would be too dark to read the
  *   labels on; `colors` again when empty
  */
case class ApexChartOptions(
  chartType: ApexChartType,
  height: Int,
  series: ApexSeries,
  colors: Option[Seq[String]] = None,
  lightColors: Option[Seq[String]] = None,
  stroke: Option[ApexStroke] = None,
  legendPosition: Option[ApexLegendPosition] = None,
  showToolbar: Boolean = false,
  dataLabelsEnabled: Boolean = true,
  nameInDataLabels: Boolean = false
)
