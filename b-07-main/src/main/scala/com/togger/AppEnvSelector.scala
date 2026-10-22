package com.togger

import pureconfig.ConfigSource
import pureconfig.generic.auto._
import scribe.Logging

case class AppConfigBe(
  isDesktopMode: Boolean
)
class AppEnvSelector(appEnv: AppEnv) extends Logging {

  def appConfig: AppConfigBe = {
    val configFile = filePicker
    logger.info(s"Running with [configFile=$configFile]")
    val config = ConfigSource
      .resources(configFile)
      .loadOrThrow[AppConfigBe]
    config
  }

  private def filePicker: String = appEnv match {
    case AppEnv.Web     => "application-web.conf"
    case AppEnv.Desktop => "application-desktop.conf"
  }

}
