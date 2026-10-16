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
    def mode(dark: Boolean) =
      ApexChartTheme.options(ApexChartPalette.fallback, dark).asInstanceOf[js.Dynamic].theme.mode.asInstanceOf[String]

    assert(mode(dark = true) == "dark")
    assert(mode(dark = false) == "light")
  }
}
