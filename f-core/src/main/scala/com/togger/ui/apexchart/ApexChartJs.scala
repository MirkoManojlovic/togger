package com.togger.ui.apexchart

import scala.scalajs.js
import scala.scalajs.js.Dynamic.literal
import scala.scalajs.js.JSConverters._

/** The only place where typed chart options become the untyped object ApexCharts reads. */
object ApexChartJs {

  def toJs(options: ApexChartOptions, theme: ApexChartTheme): js.Object = {
    val result = literal(
      "colors" -> seriesColors(options, theme).toJSArray,
      "theme" -> literal("mode" -> theme.mode.jsValue),
      "chart" -> literal(
        "type" -> options.chartType.jsValue,
        "height" -> options.height,
        "toolbar" -> literal("show" -> options.showToolbar),
        "foreColor" -> theme.foreColor,
        "background" -> "transparent"
      ),
      "grid" -> literal("borderColor" -> theme.gridBorderColor),
      "tooltip" -> literal("theme" -> theme.mode.jsValue),
      "dataLabels" -> literal(
        "enabled" -> options.dataLabelsEnabled,
        "style" -> literal("colors" -> js.Array(theme.dataLabelColor))
      )
    )
    seriesToJs(options.series).foreach { case (key, value) => result.updateDynamic(key)(value) }
    if (options.nameInDataLabels) result.dataLabels.updateDynamic("formatter")(nameAndPercent)
    options.stroke.foreach(stroke => result.updateDynamic("stroke")(strokeToJs(stroke)))
    options.legendPosition.foreach(position =>
      result.updateDynamic("legend")(literal("position" -> position.jsValue))
    )
    result
  }

  /** A slice's label reads "20% | In Progress": the percentage ApexCharts works out, then the name from the legend. */
  private val nameAndPercent: js.Function2[Double, js.Dynamic, String] = (percent, context) => {
    val name = context.w.globals.labels.asInstanceOf[js.Array[String]](context.seriesIndex.asInstanceOf[Int])
    s"${Math.round(percent)}% | $name"
  }

  private def seriesColors(options: ApexChartOptions, theme: ApexChartTheme): Seq[String] = {
    val forMode = theme.mode match {
      case ApexThemeMode.Light => options.lightColors.orElse(options.colors)
      case ApexThemeMode.Dark  => options.colors
    }
    forMode.getOrElse(theme.colors)
  }

  private def seriesToJs(series: ApexSeries): Seq[(String, js.Any)] = series match {
    case ApexSeries.Axis(named, categories) =>
      Seq(
        "series" -> named.map(s => literal("name" -> s.name, "data" -> s.data.toJSArray): js.Any).toJSArray,
        "xaxis" -> literal("categories" -> categories.toJSArray)
      )
    case ApexSeries.Slices(values, labels) =>
      Seq("series" -> values.toJSArray, "labels" -> labels.toJSArray)
  }

  private def strokeToJs(stroke: ApexStroke): js.Object =
    literal("curve" -> (if (stroke.smooth) "smooth" else "straight"), "width" -> stroke.width)
}
