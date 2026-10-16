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
}
