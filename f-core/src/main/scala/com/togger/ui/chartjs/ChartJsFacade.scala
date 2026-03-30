package com.togger.ui.chartjs

import enumeratum.{Enum, EnumEntry}
import org.scalajs.dom.Element

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal

trait Dataset2 extends js.Object {
  val label: String
  val data: js.Array[Int]
  //  backgroundColor: String,
  //  borderColor: String ,
  val borderWidth: Int
}

//@ScalaJSDefined
class Dataset1(
  label: String,
  data: Seq[Int],
  //  backgroundColor: String,
  //  borderColor: String ,
  borderWidth: Int
) extends js.Object

case class Dataset(
  label: String,
  data: Seq[Int],
//  backgroundColor: String,
//  borderColor: String ,
  borderWidth: Int
)

case class ChartOptions(
  scales: ChartScales
)

case class ChartScales(y: ChartScaleOption)

case class ChartScaleOption(beginAtZero: Boolean)

sealed trait ChartType extends EnumEntry {
  def jsValue: String
}

object ChartType extends Enum[ChartType] {
  override def values: IndexedSeq[ChartType] = findValues

  case class Bar(jsValue: "bar")
}

class ChartData(
  labels: Seq[String],
  datasets: Seq[Dataset]
)

@js.native
@JSGlobal
class Chart(
  val ctx: Element,
  val config: js.Object = js.native
) extends js.Object {}
//
//@js.native
//@JSGlobal
//class ChartConfig(
//  chartData: ChartData,
//  options: ChartOptions
//)
