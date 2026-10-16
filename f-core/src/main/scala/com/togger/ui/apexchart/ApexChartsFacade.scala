package com.togger.ui.apexchart

import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal

@js.native
@JSGlobal("ApexCharts")
class ApexCharts(el: dom.Element, options: js.Object) extends js.Object {
  def render(): js.Promise[Unit] = js.native

  def updateOptions(options: js.Object): js.Promise[Unit] = js.native

  def destroy(): Unit = js.native
}
