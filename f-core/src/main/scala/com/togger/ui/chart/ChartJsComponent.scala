package com.togger.ui.chart

import cats.effect.unsafe.IORuntime
import com.togger.backendcalls.UnprotectedBackendClient
import com.togger.dto.LogEntryDto
import com.togger.logentry.LogEntryEndpoints
import com.togger.logentry.LogEntryStatus.{Blocked, InProgress}
import com.togger.ui.chartjs.Chart
import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.scalajs.dom
import org.scalajs.dom.HTMLDivElement
import sttp.client3.UriContext

import java.time.ZoneOffset
import scala.collection.mutable.ArrayBuffer
import scala.scalajs.js
import scala.scalajs.js.Dynamic.literal
import scala.scalajs.js.JSConverters._

class ChartJsComponent()(implicit runtime: IORuntime) {

  val pieChartElement = dom.document.querySelector("#pie-chart-element")
  val lineChartElement = dom.document.querySelector("#line-chart-element")
  var logEntriesVar: Var[List[LogEntryDto]] = Var(List.empty)

  // backend call
  val logEntryEndpoints = new LogEntryEndpoints(null)

  val unprotectedBackendClient = new UnprotectedBackendClient(baseUri = uri"http://localhost:8081/")

//  val m = unprotectedBackendClient.httpCall(logEntryEndpoints.findByTaskId)(2)
val m = null
//  m.map(_.map(success => logEntriesVar.set(success.body.get))).unsafeRunAndForget()

  // ---

  case class ChartColors(backgroundColor: String, borderColor: String)

  object ChartColors {

    val map1: Map[String, ChartColors] = Map(
      "blocked" -> ChartColors("rgba(255, 99, 132, 0.2)", "rgb(255, 99, 132)"), // red
//    ChartColors("rgba(255, 159, 64, 0.2)", "rgb(255, 159, 64)"), // orange
      "not_sure" -> ChartColors("rgba(255, 205, 86, 0.2)", "rgb(255, 205, 86)"), // yellow
      "done" -> ChartColors("rgba(75, 192, 192, 0.2)", "rgb(75, 192, 192)"), // green
      "inprog" -> ChartColors("rgba(54, 162, 235, 0.2)", "rgb(54, 162, 235)"), // blue
//    ChartColors("rgba(153, 102, 255, 0.2)", "rgb(153, 102, 255)"), // purple
      "cancled" -> ChartColors("rgba(201, 203, 207, 0.2)", "rgb(201, 203, 207)") // gray
    )

  }

  case class ChartNewData(
    labels: js.Array[String],
    data: js.Array[Int],
    backgroundColor: js.Array[String],
    borderColor: js.Array[String]
  )

  def createPieChartData(logEntries: Seq[LogEntryDto]): ChartNewData = {
    val labels = ArrayBuffer[String]()
    val data = ArrayBuffer.empty[Int]
    val backgroundColor = ArrayBuffer.empty[String]
    val borderColor = ArrayBuffer.empty[String]

    val groupedEntries = logEntries.groupBy(_.status).map { case (status, entries) => (status, entries.size) }

    groupedEntries.foreach { case (status, count) =>
      val chartColors = ChartColors.map1(status.toString)
      labels += status.toString
      data += count
      backgroundColor += chartColors.backgroundColor
      borderColor += chartColors.borderColor
    }

    ChartNewData(labels.toJSArray, data.toJSArray, backgroundColor.toJSArray, borderColor.toJSArray)
  }

  def createLineChartData(logEntries: Seq[LogEntryDto]): ChartNewData = {
    val labels = ArrayBuffer[String]()
    val data = ArrayBuffer.empty[Int]
    val backgroundColor = ArrayBuffer.empty[String]
    val borderColor = ArrayBuffer.empty[String]

    val groupedEntries = logEntries.groupBy(_.status).map { case (status, entries) => (status, entries.size) }

    logEntries.map { dto =>
      labels += dto.createdAt.atOffset(ZoneOffset.UTC).toLocalDate.toString
      val statusAsInt = dto.status match {
        case Blocked => 0
        case InProgress  => 1
      }
      data += statusAsInt
    }

    ChartNewData(labels.toJSArray, data.toJSArray, backgroundColor.toJSArray, borderColor.toJSArray)
  }

  val htmlElementSig: Signal[ReactiveHtmlElement[HTMLDivElement]] = logEntriesVar.signal.map { logEntries =>
    if (logEntries.isEmpty) {
      div("empty")
    } else {
      val pieChartData = createPieChartData(logEntries)

//      println(pieChartData)

      new Chart(
        ctx = pieChartElement,
        config = literal(
          "type" -> "pie",
          "data" -> literal(
            "labels" -> pieChartData.labels,
            "datasets" -> js.Array(
              literal(
                "label" -> "# number of days",
                "data" -> pieChartData.data,
                "backgroundColor" -> pieChartData.backgroundColor,
                "borderColor" -> pieChartData.borderColor,
                "borderWidth" -> 2
              )
            )
          ),
          "options" -> literal(
            "scales" -> literal(
              "y" -> literal("beginAtZero" -> true)
            )
          )
        )
      )
      val lineChartData = createLineChartData(logEntries)

      new Chart(
        ctx = lineChartElement,
        config = literal(
          "type" -> "line",
          "data" -> literal(
            "labels" -> lineChartData.labels,
            "datasets" -> js.Array(
              literal(
                "label" -> "# number of days",
                "data" -> lineChartData.data,
//                "backgroundColor" -> lineChartData.backgroundColor,
                "borderColor" -> lineChartData.borderColor,
                "borderWidth" -> 3
              )
            )
          ),
          "options" -> literal(
            "scales" -> literal(
              "y" -> literal("beginAtZero" -> true)
            )
          )
        )
      )

      div("not empty")
    }

  }

  def run: Chart = {

//    new Chart(
//      ctx = chartElement,
//      config = ChartConfig(
//        chartData = new ChartData(
//          labels = Seq("Red", "Blue", "Yellow", "Green", "Purple", "Orange"),
//          datasets = Seq(
//            Dataset(
//              label = "# of Votes",
//              data = Seq(12, 19, 3, 5, 2, 3),
//              borderWidth = 1
//            )
//          )
//        ),
//        options = ChartOptions(
//          scales = ChartScales(y = ChartScaleOption(beginAtZero = true))
//        )
//      )
//    )

    new Chart(
      ctx = pieChartElement,
      config = literal(
        "type" -> "pie",
        "data" -> literal(
          "labels" -> js.Array("Red", "Blue", "Yellow", "Green", "Purple", "Orange"),
          "datasets" -> js.Array(
            literal(
              "label" -> "# of Votes",
              "data" -> js.Array(12, 19, 20, 5, 2, 3, 9),
              "backgroundColor" -> js.Array(
                "rgba(255, 99, 132, 0.2)",
                "rgba(255, 159, 64, 0.2)",
                "rgba(255, 205, 86, 0.2)",
                "rgba(75, 192, 192, 0.2)",
                "rgba(54, 162, 235, 0.2)",
                "rgba(153, 102, 255, 0.2)",
                "rgba(201, 203, 207, 0.2)"
              ),
              "borderColor" -> js.Array(
                "rgb(255, 99, 132)",
                "rgb(255, 159, 64)",
                "rgb(255, 205, 86)",
                "rgb(75, 192, 192)",
                "rgb(54, 162, 235)",
                "rgb(153, 102, 255)",
                "rgb(201, 203, 207)"
              ),
              "borderWidth" -> 3
            )
          )
        ),
        "options" -> literal(
          "scales" -> literal(
            "y" -> literal("beginAtZero" -> true)
          )
        )
      )
    )

  }
}
