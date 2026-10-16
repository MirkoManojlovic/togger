package com.togger.ui.apexchart

import com.raquo.laminar.api.L._
import org.scalajs.dom

import scala.scalajs.js

class ApexChartComponent(chartOptions: Signal[js.Object], isDark: Signal[Boolean], heightPx: Int = 320) {

  private def readPalette(): ApexChartPalette = {
    val style = dom.window.getComputedStyle(dom.document.documentElement)
    ApexChartPalette.fromVariables(name => style.getPropertyValue(name))
  }

  private def merged(options: js.Object, dark: Boolean): js.Object = {
    val result = js.Dictionary[js.Any]()
    val theme = ApexChartTheme.options(readPalette(), dark).asInstanceOf[js.Dictionary[js.Any]]
    val data = options.asInstanceOf[js.Dictionary[js.Any]]
    theme.foreach { case (k, v) => result(k) = v }
    data.foreach { case (k, v) =>
      result(k) = (result.get(k), v) match {
        case (Some(base), extra) if js.typeOf(base) == "object" && js.typeOf(extra) == "object" =>
          js.Object.assign(js.Dynamic.literal(), base.asInstanceOf[js.Object], extra.asInstanceOf[js.Object])
        case _ => v
      }
    }
    result.asInstanceOf[js.Object]
  }

  val element: HtmlElement = {
    var chart: Option[ApexCharts] = None
    div(
      cls("w-full"),
      minHeight(s"${heightPx}px"),
      onMountBind { ctx =>
        chartOptions.combineWith(isDark) --> Observer[(js.Object, Boolean)] { case (options, dark) =>
          // wait a frame so the `.dark` class (applied from the same signal) is reflected in computed styles
          dom.window.requestAnimationFrame { _ =>
            val all = merged(options, dark)
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
