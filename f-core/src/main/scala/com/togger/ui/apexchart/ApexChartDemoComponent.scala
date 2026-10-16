package com.togger.ui.apexchart

import com.raquo.laminar.api.L._

import scala.scalajs.js
import scala.scalajs.js.Dynamic.literal

class ApexChartDemoComponent(isDark: Signal[Boolean]) {

  private val areaOptions: js.Object = literal(
    "chart" -> literal("type" -> "area", "height" -> 320, "toolbar" -> literal("show" -> false)),
    "series" -> js.Array(literal("name" -> "Logged tasks", "data" -> js.Array(6, 9, 7, 12, 10, 4, 3))),
    "xaxis" -> literal("categories" -> js.Array("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")),
    "stroke" -> literal("curve" -> "smooth", "width" -> 4),
    "dataLabels" -> literal("enabled" -> false)
  )

  private val donutOptions: js.Object = literal(
    "chart" -> literal("type" -> "donut", "height" -> 320),
    "series" -> js.Array(14, 9, 5, 2),
    "labels" -> js.Array("Done", "In progress", "Blocked", "Cancelled"),
    "colors" -> js.Array("#1447E6", "#16a34a", "#dc2626", "#9ca3af"),
    "legend" -> literal("position" -> "bottom")
  )

  private def card(title: String, chart: ApexChartComponent): HtmlElement =
    div(
      cls("bg-neutral-primary-soft border border-default rounded-base shadow-xs p-4 md:p-6"),
      h5(cls("text-heading text-lg font-semibold mb-4"), title),
      chart.element
    )

  val element: HtmlElement =
    div(
      cls("grid grid-cols-1 lg:grid-cols-2 gap-4"),
      card("Tasks per day", new ApexChartComponent(Val(areaOptions), isDark)),
      card("Tasks by status", new ApexChartComponent(Val(donutOptions), isDark))
    )
}
