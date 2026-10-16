package com.togger.ui.apexchart

import com.raquo.laminar.api.L._

class ApexChartDemoComponent(isDark: Signal[Boolean]) {

  private val areaOptions = ApexChartOptions(
    chartType = ApexChartType.Area,
    height = 320,
    series = ApexSeries.Axis(
      series = Seq(ApexNamedSeries("Logged tasks", Seq(6, 9, 7, 12, 10, 4, 3))),
      categories = Seq("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    ),
    stroke = Some(ApexStroke(smooth = true, width = 4)),
    dataLabelsEnabled = false
  )

  private val donutOptions = ApexChartOptions(
    chartType = ApexChartType.Donut,
    height = 320,
    series = ApexSeries.Slices(
      values = Seq(14, 9, 5, 2),
      labels = Seq("Done", "In progress", "Blocked", "Cancelled")
    ),
    colors = Some(Seq("#1447E6", "#16a34a", "#dc2626", "#9ca3af")),
    legendPosition = Some(ApexLegendPosition.Bottom)
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
