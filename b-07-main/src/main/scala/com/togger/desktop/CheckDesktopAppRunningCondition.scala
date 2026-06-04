package com.togger.desktop

class CheckDesktopAppRunningCondition {

  def isRunning: Boolean = {
    val res = os.proc("ps", "aux").pipeTo(os.proc("grep", "togger")).call().out.text()
    checkToggerMacOS(res)
  }

  private def checkToggerMacOS(output: String): Boolean = {
    output.contains("togger.app/Contents/MacOS/app")
  }

}
