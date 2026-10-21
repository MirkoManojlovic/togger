package com.togger.ui.apexchart

import org.scalatest.funsuite.AnyFunSuite

import scala.scalajs.js

class ApexChartThemeSpec extends AnyFunSuite {

  test("empty css variables fall back to the default palette") {
    assert(ApexChartPalette.fromVariables(_ => "  ") == ApexChartPalette.fallback)
  }

  test("css variables override the fallback") {
    val palette = ApexChartPalette.fromVariables { case "--color-fg-brand" => " #123456 "; case _ => "" }
    assert(palette.brand == "#123456")
    assert(palette.body == ApexChartPalette.fallback.body)
  }

  test("theme mode follows isDark") {
    assert(ApexChartTheme(ApexChartPalette.fallback, isDark = true).mode == ApexThemeMode.Dark)
    assert(ApexChartTheme(ApexChartPalette.fallback, isDark = false).mode == ApexThemeMode.Light)
  }

  test("toJs uses explicit colors over theme colors and converts series") {
    val options = ApexChartOptions(
      ApexChartType.Donut,
      200,
      ApexSeries.Slices(Seq(1, 2), Seq("a", "b")),
      colors = Some(Seq("#000"))
    )
    val js1 = ApexChartJs.toJs(options, ApexChartTheme(ApexChartPalette.fallback, isDark = true)).asInstanceOf[js.Dynamic]

    assert(js1.colors.asInstanceOf[js.Array[String]].toList == List("#000"))
    assert(js1.series.asInstanceOf[js.Array[Int]].toList == List(1, 2))
    assert(js1.labels.asInstanceOf[js.Array[String]].toList == List("a", "b"))
    assert(js1.theme.mode.asInstanceOf[String] == "dark")
  }

  test("toJs picks the light colors in light mode and falls back to the colors without them") {
    val options = ApexChartOptions(
      ApexChartType.Pie,
      200,
      ApexSeries.Slices(Seq(1), Seq("a")),
      colors = Some(Seq("#111")),
      lightColors = Some(Seq("#eee"))
    )
    def colorsIn(options: ApexChartOptions, isDark: Boolean): List[String] =
      ApexChartJs
        .toJs(options, ApexChartTheme(ApexChartPalette.fallback, isDark))
        .asInstanceOf[js.Dynamic]
        .colors
        .asInstanceOf[js.Array[String]]
        .toList

    assert(colorsIn(options, isDark = false) == List("#eee"))
    assert(colorsIn(options, isDark = true) == List("#111"))
    assert(colorsIn(options.copy(lightColors = None), isDark = false) == List("#111"))
  }

  private def chartOf(options: ApexChartOptions): js.Dynamic =
    ApexChartJs
      .toJs(options, ApexChartTheme(ApexChartPalette.fallback, isDark = false))
      .asInstanceOf[js.Dynamic]

  private val columns = ApexChartOptions(
    ApexChartType.Column,
    200,
    ApexSeries.Axis(Seq(ApexNamedSeries("Entries", Seq(1, 2)), ApexNamedSeries("Tasks", Seq(3, 4))), Seq("Ann", "Bob"))
  )

  test("toJs makes a column chart a standing bar chart with the series and the categories") {
    val js1 = chartOf(columns)
    assert(js1.chart.`type`.asInstanceOf[String] == "bar")
    assert(!js1.plotOptions.bar.horizontal.asInstanceOf[Boolean])
    assert(!js1.chart.stacked.asInstanceOf[Boolean])
    assert(js1.xaxis.categories.asInstanceOf[js.Array[String]].toList == List("Ann", "Bob"))
    assert(
      js1.series.asInstanceOf[js.Array[js.Dynamic]].map(_.name.asInstanceOf[String]).toList == List("Entries", "Tasks")
    )
  }

  test("toJs makes a bar chart lie and stacks the series when asked to") {
    val js1 = chartOf(columns.copy(chartType = ApexChartType.Bar, stacked = true))
    assert(js1.chart.`type`.asInstanceOf[String] == "bar")
    assert(js1.plotOptions.bar.horizontal.asInstanceOf[Boolean])
    assert(js1.chart.stacked.asInstanceOf[Boolean])
  }

  test("toJs gives the other chart types no bar options") {
    assert(js.isUndefined(chartOf(columns.copy(chartType = ApexChartType.Area)).plotOptions))
  }
}
