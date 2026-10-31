package com.togger
import scala.annotation.nowarn
import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobalScope

// shows dead code warnings
@nowarn
@js.native
trait AppConfig extends js.Object {
  val isDesktopMode: String = js.native
  val isPreviewMode: String = js.native
  val previewBuildId: String = js.native
}

// shows dead code warnings
@nowarn
@js.native
@JSGlobalScope
object AppConfigGlobals extends js.Object {
  val appConfig: AppConfig = js.native
}
