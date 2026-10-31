package com.togger.preview

/** Frontend preview mode: no backend, every call is answered by a `*Preview` service backed by localStorage. */
case class PreviewConfig(enabled: Boolean, buildId: String) {
  def storagePrefix: String = if (enabled) PreviewConfig.prefixFor(buildId) else ""
}

object PreviewConfig {
  val disabled: PreviewConfig = PreviewConfig(enabled = false, buildId = "")

  def prefixFor(buildId: String): String = s"$buildId:"
}
