package com.togger.ui.tgprompt

import com.togger.dto.TgPromptVersionDto
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant
import java.util.UUID

class TgPromptFormSupportTest extends AnyWordSpec with Matchers {

  private def version(createdAt: Long) =
    TgPromptVersionDto(UUID.randomUUID(), UUID.randomUUID(), "title", "prompt", Instant.ofEpochSecond(createdAt))

  "TgPromptFormSupport.preview" should {

    "use the first non blank line" in {
      TgPromptFormSupport.preview("\n  \n  Summarise this \nsecond") shouldBe "Summarise this"
    }

    "cut a long line with an ellipsis" in {
      val preview = TgPromptFormSupport.preview("a" * 200)
      preview should have length 80
      preview should endWith("…")
    }

    "be empty for a blank prompt" in {
      TgPromptFormSupport.preview("  \n ") shouldBe ""
    }
  }

  "TgPromptFormSupport.newestFirst" should {
    "order the versions by creation time, newest first" in {
      val (old, recent) = (version(1), version(2))
      TgPromptFormSupport.newestFirst(Seq(old, recent)) shouldBe Seq(recent, old)
    }
  }

  "TgPromptFormSupport.canDeleteVersion" should {
    "keep the last remaining version" in {
      TgPromptFormSupport.canDeleteVersion(Seq(version(1))) shouldBe false
      TgPromptFormSupport.canDeleteVersion(Seq(version(1), version(2))) shouldBe true
    }
  }
}
