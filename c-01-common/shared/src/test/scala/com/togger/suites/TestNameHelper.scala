package com.togger.suites

trait TestNameHelper {
  def testNameFromClassName: String = this.getClass.getSimpleName.replace("Test", "")
}
