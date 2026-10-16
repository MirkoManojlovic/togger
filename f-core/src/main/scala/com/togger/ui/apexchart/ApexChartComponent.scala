package com.togger.ui.apexchart

import com.raquo.laminar.api.L._
import org.scalajs.dom

class ApexChartComponent(chartOptions: Signal[ApexChartOptions], isDark: Signal[Boolean], heightPx: Int = 320) {

  private def readPalette(): ApexChartPalette = {
    val style = dom.window.getComputedStyle(dom.document.documentElement)
    ApexChartPalette.fromVariables(name => style.getPropertyValue(name))
  }

  val element: HtmlElement = {
    var chart: Option[ApexCharts] = None
    div(
      cls("w-full"),
      minHeight(s"${heightPx}px"),
      onMountBind { ctx =>
        chartOptions.combineWith(isDark) --> Observer[(ApexChartOptions, Boolean)] { case (options, dark) =>
          // wait a frame so the `.dark` class (applied from the same signal) is reflected in computed styles
          dom.window.requestAnimationFrame { _ =>
            val all = ApexChartJs.toJs(options, ApexChartTheme(readPalette(), dark))
            chart match {
              case Some(existing) => existing.updateOptions(all)
              case None =>
                val created = new ApexCharts(ctx.thisNode.ref, all)
                created.render()
                chart = Some(created)
            }
          }
        }
      },
      onUnmountCallback { _ =>
        chart.foreach(_.destroy())
        chart = None
      }
    )
  }
}
