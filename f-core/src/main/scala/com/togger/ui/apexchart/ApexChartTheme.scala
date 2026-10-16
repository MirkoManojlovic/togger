package com.togger.ui.apexchart

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

sealed trait ApexThemeMode {
  def jsValue: String
}

object ApexThemeMode {
  case object Light extends ApexThemeMode { val jsValue = "light" }
  case object Dark extends ApexThemeMode { val jsValue = "dark" }

  def fromIsDark(isDark: Boolean): ApexThemeMode = if (isDark) Dark else Light
}

case class ApexChartTheme(
  mode: ApexThemeMode,
  colors: Seq[String],
  foreColor: String,
  gridBorderColor: String,
  dataLabelColor: String
)

object ApexChartTheme {

  def apply(palette: ApexChartPalette, isDark: Boolean): ApexChartTheme =
    ApexChartTheme(
      mode = ApexThemeMode.fromIsDark(isDark),
      colors = Seq(palette.brand),
      foreColor = palette.body,
      gridBorderColor = palette.border,
      dataLabelColor = palette.heading
    )
}
