package com.togger.desktop

class CheckDesktopAppRunningCondition {

  def isRunning: Boolean = {
    val res = os.proc("ps", "aux").pipeTo(os.proc("grep", "Togger")).call().out.text()
    checkToggerMacOS(res)
  }

  private def checkToggerMacOS(output: String): Boolean = {
    output.contains("Togger.app/Contents/MacOS/app")
  }

}
