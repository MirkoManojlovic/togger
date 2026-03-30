package com.togger

import com.togger.routes.{TgRouterImpl, TgRoutes}
import com.raquo.laminar.api.L._
import org.scalajs.dom

object MainAppFe extends App {
  println("Hello from app")

  private val app = dom.document.querySelector("#app")

  val origin = "http://localhost:5173"
  val initialUrl = "http://localhost:5173/"
  val basePath = "/"

  val routes = new TgRoutes().routes(basePath)

  val router = new TgRouterImpl(routes, origin, initialUrl)
  private val splitRender = new TgSplitRender(router)

  render(
    app,
    div(
      child <-- splitRender.splitRender.signal
    )
  )
}
