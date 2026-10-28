package com.togger.ui.tgprompttemplate

import com.togger.dto.TgPromptTemplateVersionDto
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant
import java.util.UUID

class TgPromptTemplateFormSupportTest extends AnyWordSpec with Matchers {

  private def version(createdAt: Long) =
    TgPromptTemplateVersionDto(UUID.randomUUID(), UUID.randomUUID(), "title", "prompt", Instant.ofEpochSecond(createdAt))

  "TgPromptTemplateFormSupport.preview" should {

    "use the first non blank line" in {
      TgPromptTemplateFormSupport.preview("\n  \n  Summarise this \nsecond") shouldBe "Summarise this"
    }

    "cut a long line with an ellipsis" in {
      val preview = TgPromptTemplateFormSupport.preview("a" * 200)
      preview should have length 80
      preview should endWith("…")
    }

    "be empty for a blank prompt" in {
      TgPromptTemplateFormSupport.preview("  \n ") shouldBe ""
    }
  }

  "TgPromptTemplateFormSupport.newestFirst" should {
    "order the versions by creation time, newest first" in {
      val (old, recent) = (version(1), version(2))
      TgPromptTemplateFormSupport.newestFirst(Seq(old, recent)) shouldBe Seq(recent, old)
    }
  }

  "TgPromptTemplateFormSupport.canDeleteVersion" should {
    "keep the last remaining version" in {
      TgPromptTemplateFormSupport.canDeleteVersion(Seq(version(1))) shouldBe false
      TgPromptTemplateFormSupport.canDeleteVersion(Seq(version(1), version(2))) shouldBe true
    }
  }
}
