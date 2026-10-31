package com.togger.preview

import com.togger.ai.TgAiEndpoints
import com.togger.common.TgClock
import com.togger.dto.TgPromptTemplateVersionDto

import java.time.temporal.ChronoUnit
import java.util.UUID

/** Seeds prompt templates, AI actions and default actions for the demo account and one for the second account. */
class AiPreviewSeed(data: AiPreviewData, clock: TgClock) extends PreviewSeeder {
  import AiPreviewSeed._

  override def seed(): Unit = {
    val now = clock.currentInstant
    prompts.foreach { prompt =>
      data.promptTemplates.upsert(
        PreviewPromptTemplate(prompt.id, prompt.owner, now.minus(prompt.versions.size * 7L + prompt.number, ChronoUnit.DAYS))
      )
      prompt.versions.zipWithIndex.foreach { case ((title, text), i) =>
        data.promptVersions.upsert(
          TgPromptTemplateVersionDto(
            PreviewSeedIds.id(PreviewSeedIds.PromptTemplate, prompt.number * 10 + i + 100),
            prompt.id,
            title,
            text,
            now.minus((prompt.versions.size - i) * 7L + prompt.number, ChronoUnit.DAYS)
          )
        )
      }
    }
    actions.foreach { action =>
      val created = now.minus(30L - action.number * 4L, ChronoUnit.DAYS)
      data.actions.upsert(
        PreviewAiAction(
          action.id,
          action.owner,
          action.name,
          action.prompt,
          action.promptTemplateId,
          action.model,
          List(MilestoneSummaryEndpointId),
          created,
          created
        )
      )
    }
    defaults.zipWithIndex.foreach { case ((projectId, actionId), i) =>
      data.defaults.upsert(
        PreviewAiActionDefault(PreviewSeedIds.id(PreviewSeedIds.AiActionDefault, i + 1), PreviewDemo.AccountId, projectId, MilestoneSummaryEndpointId, actionId)
      )
    }
  }
}

object AiPreviewSeed {
  val MilestoneSummaryEndpointId: Int = TgAiEndpoints.MilestoneSummary.value

  private case class PromptSpec(number: Int, owner: UUID, versions: List[(String, String)]) {
    def id: UUID = PreviewSeedIds.id(PreviewSeedIds.PromptTemplate, number)
  }

  private case class ActionSpec(
    number: Int,
    owner: UUID,
    name: String,
    prompt: String,
    promptTemplateId: Option[UUID],
    model: String
  ) {
    def id: UUID = PreviewSeedIds.id(PreviewSeedIds.AiAction, number)
  }

  private val weeklyPrompts = List(
    "Weekly status report" -> "Summarise the log entries of the period as a short status report for the team.",
    "Weekly status report" ->
      "Summarise the log entries of the period as a status report for the team.\nGroup the work by task, mention blockers and finish with the next steps."
  )

  private val prompts = List(
    PromptSpec(1, PreviewDemo.AccountId, weeklyPrompts),
    PromptSpec(
      2,
      PreviewDemo.AccountId,
      List(
        "Executive summary" ->
          "Write a summary for stakeholders in at most five sentences. Focus on outcomes and risks, not on technical details."
      )
    ),
    PromptSpec(
      3,
      PreviewDemo.AccountId,
      List("Risks and blockers" -> "List everything that slowed the team down in the period and what could still go wrong.")
    ),
    PromptSpec(
      4,
      PreviewDemo.SecondAccountId,
      List("Sprint recap" -> "Recap the sprint in a friendly tone, one paragraph per milestone.")
    )
  )

  private val actions = List(
    ActionSpec(1, PreviewDemo.AccountId, "Weekly summary", "Use a neutral tone and bullet points.", Some(prompts(0).id), "gpt-4.1-mini"),
    ActionSpec(2, PreviewDemo.AccountId, "Executive brief", "Keep it under 120 words.", Some(prompts(1).id), "gpt-4.1"),
    ActionSpec(3, PreviewDemo.AccountId, "Risk review", "Highlight the three biggest risks first.", Some(prompts(2).id), "gpt-4o"),
    ActionSpec(
      4,
      PreviewDemo.AccountId,
      "Quick recap",
      "Summarise the log entries of the period in a few bullet points.",
      None,
      "gpt-4.1-nano"
    ),
    ActionSpec(5, PreviewDemo.SecondAccountId, "Sprint recap", "Friendly tone please.", Some(prompts(3).id), "gpt-4o-mini")
  )

  private val defaults = List(
    PreviewDemo.ProjectId -> PreviewSeedIds.id(PreviewSeedIds.AiAction, 1),
    PreviewDemo.SecondProjectId -> PreviewSeedIds.id(PreviewSeedIds.AiAction, 4)
  )
}
