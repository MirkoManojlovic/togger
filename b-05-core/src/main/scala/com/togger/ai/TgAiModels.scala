package com.togger.ai

final case class TgAiApiKey(value: String) extends AnyVal {
  override def toString: String = "TgAiApiKey(***)"
}

final case class TgAiModel(id: String) extends AnyVal

sealed trait TgAiMessage {
  def text: String
}

object TgAiMessage {
  final case class System(text: String) extends TgAiMessage
  final case class User(text: String) extends TgAiMessage
  final case class Assistant(text: String) extends TgAiMessage
}

final case class TgAiChatRequest(
    model: TgAiModel,
    messages: List[TgAiMessage],
    maxOutputTokens: Option[Int] = None
)

final case class TgAiUsage(promptTokens: Int, completionTokens: Int, totalTokens: Int)

final case class TgAiChatResult(text: String, usage: Option[TgAiUsage])

final case class TgAiChunk(delta: String)
