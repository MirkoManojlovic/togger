package com.togger.ui.apexchart

import scala.scalajs.js
import scala.scalajs.js.Dynamic.literal

case class ApexChartPalette(brand: String, body: String, heading: String, border: String)

object ApexChartPalette {

  val fallback: ApexChartPalette = ApexChartPalette("#1447E6", "#4b5563", "#111827", "#e5e7eb")

  def fromVariables(read: String => String): ApexChartPalette = {
    def value(name: String, default: String): String = Option(read(name)).map(_.trim).filter(_.nonEmpty).getOrElse(default)

    ApexChartPalette(
      brand = value("--color-fg-brand", fallback.brand),
      body = value("--color-body", fallback.body),
      heading = value("--color-heading", fallback.heading),
      border = value("--color-default", fallback.border)
    )
  }
}

object ApexChartTheme {

  def options(palette: ApexChartPalette, isDark: Boolean): js.Object =
    literal(
      "colors" -> js.Array(palette.brand),
      "theme" -> literal("mode" -> (if (isDark) "dark" else "light")),
      "chart" -> literal("foreColor" -> palette.body, "background" -> "transparent"),
      "grid" -> literal("borderColor" -> palette.border),
      "tooltip" -> literal("theme" -> (if (isDark) "dark" else "light")),
      "dataLabels" -> literal("style" -> literal("colors" -> js.Array(palette.heading)))
    )
}
