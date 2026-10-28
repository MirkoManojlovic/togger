package com.togger.ui.tgaiaction

import com.togger.dto.{FindTgAiActionResp, TgAiEndpointDto}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant
import java.util.UUID

class TgAiActionFormSupportTest extends AnyWordSpec with Matchers {

  private val templateId = UUID.randomUUID()

  private val dto = FindTgAiActionResp(
    id = UUID.randomUUID(),
    name = "Summarise",
    prompt = "Summarise this",
    tgPromptTemplateId = Some(templateId),
    model = "gpt-5",
    endpointIds = List(0, 2),
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH
  )

  "TgAiActionFormSupport" should {

    "turn a dto into a form and back into an update request" in {
      val form = TgAiActionFormSupport.fromDto(dto)
      form.tgPromptTemplateId shouldBe templateId.toString
      form.endpointIds shouldBe Set(0, 2)

      val request = TgAiActionFormSupport.toUpdateReq(form)
      request.id shouldBe dto.id
      request.tgPromptTemplateId shouldBe Some(templateId)
      request.endpointIds shouldBe List(0, 2)
    }

    "send no prompt template when none is selected" in {
      val request = TgAiActionFormSupport.toCreateReq(TgAiActionForm(name = "n", prompt = "p", model = "m"))
      request.tgPromptTemplateId shouldBe None
      request.endpointIds shouldBe Nil
    }

    "add and remove an endpoint" in {
      val form = TgAiActionForm()
      val added = TgAiActionFormSupport.toggleEndpoint(form, 1, selected = true)
      added.endpointIds shouldBe Set(1)
      TgAiActionFormSupport.toggleEndpoint(added, 1, selected = false).endpointIds shouldBe Set.empty
    }

    "list the names of the bound endpoints in endpoint order" in {
      val endpoints = Seq(TgAiEndpointDto(0, "milestone-summary"), TgAiEndpointDto(1, "other"), TgAiEndpointDto(2, "third"))
      TgAiActionFormSupport.endpointNames(List(2, 0), endpoints) shouldBe "milestone-summary, third"
    }
  }
}
