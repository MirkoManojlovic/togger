package com.togger

import com.raquo.laminar.api.L._
import com.togger.preview.PreviewConfig
import com.togger.routes.{TgRouterImpl, TgRoutes}
import org.scalajs.dom

import scala.concurrent.duration._
import scala.scalajs.js.timers._

object FrontendApp extends App {
  val appConfig = AppConfigGlobals.appConfig

  val previewConfig = PreviewConfig(appConfig.isPreviewMode == "true", appConfig.previewBuildId)

  val webFrontendApp = new WebFrontendApp(previewConfig)
  val desktopFrontendApp = new DesktopFrontendApp

  if (appConfig.isDesktopMode.toBoolean) {
    require(!previewConfig.enabled, "Frontend preview mode is web only")
    desktopFrontendApp.run
  } else webFrontendApp.run
}

class WebFrontendApp(previewConfig: PreviewConfig) {
  private val app = dom.document.querySelector("#app")
  private val splashScreen = dom.document.querySelector("#splash-screen")

  def run: Unit = {
    splashScreen.remove()

    val origin = "http://localhost:5173"
    val initialUrl = "http://localhost:5173/"
    val basePath = "/"

    val routes = new TgRoutes().routes(basePath)

    val router = new TgRouterImpl(routes, origin, None)
    val splitRender = new TgSplitRender(router, previewConfig)

    render(
      app,
      div(
        child <-- splitRender.splitRender.signal
      )
    )
  }

}

class DesktopFrontendApp {
  private val app = dom.document.querySelector("#app")
  private val splashScreen = dom.document.querySelector("#splash-screen")
  private val sleepTime = 3.seconds

  def run: Unit = {
    val origin = "tauri://localhost"
    val initialUrl = "tauri://localhost/"
    val basePath = "/"

    setTimeout(sleepTime) {
      val routes = new TgRoutes().routes(basePath)

      val router = new TgRouterImpl(routes, origin, Some(initialUrl))
      val splitRender = new TgSplitRender(router)

      splashScreen.remove()

      render(
        app,
        div(
          child <-- splitRender.splitRender.signal
        )
      )
    }
  }
}
