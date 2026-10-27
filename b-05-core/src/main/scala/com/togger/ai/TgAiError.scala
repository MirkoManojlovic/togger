package com.togger.ai

import com.togger.common.TgError

object TgAiError {
  sealed trait TgAiError extends TgError

  case object TgAiApiKeyMissing extends TgAiError {
    override def message: String = "AI API key is not configured"
  }

  case object TgAiAuthentication extends TgAiError {
    override def message: String = "AI provider rejected the API key"
  }

  case object TgAiRateLimited extends TgAiError {
    override def message: String = "AI provider rate limit reached, try again later"
  }

  case class TgAiInvalidRequest(reason: String) extends TgAiError {
    override def message: String = s"Invalid AI request: $reason"
  }

  case class TgAiProviderUnavailable(reason: String) extends TgAiError {
    override def message: String = s"AI provider unavailable: $reason"
  }

  case object TgAiEmptyResponse extends TgAiError {
    override def message: String = "AI provider returned an empty response"
  }

  case class TgAiResponseParseFailed(reason: String) extends TgAiError {
    override def message: String = s"Could not parse AI response: $reason"
  }

  case class TgAiUnexpected(reason: String) extends TgAiError {
    override def message: String = s"Unexpected AI error: $reason"
  }
}
