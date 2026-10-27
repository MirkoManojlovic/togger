package com.togger.ai

import sttp.model.Uri

import scala.concurrent.duration._

/** The local Ollama server through its OpenAI compatible API; it needs no authentication, the key is a placeholder. */
object TgOllama {
  val BaseUri: Uri = Uri.unsafeParse("http://localhost:11434/v1")
  val ApiKey: TgAiApiKey = TgAiApiKey("ollama")
  val ReadTimeout: FiniteDuration = 5.minutes
}
