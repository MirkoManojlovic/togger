package com.togger.demo

import com.togger.ai.TgAiEndpoints
import com.togger.dto.TgPromptTemplateVersionDto

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Prompt templates, AI actions and default actions for the demo account and one for the second account. */
object DemoAiData {
  val MilestoneSummaryEndpoint: TgAiEndpoints = TgAiEndpoints.MilestoneSummary

  private case class PromptSpec(number: Int, owner: UUID, versions: List[(String, String)]) {
    def id: UUID = DemoSeedIds.id(DemoSeedIds.PromptTemplate, number)
  }

  private case class ActionSpec(
    number: Int,
    owner: UUID,
    name: String,
    prompt: String,
    promptTemplateId: Option[UUID],
    model: String
  ) {
    def id: UUID = DemoSeedIds.id(DemoSeedIds.AiAction, number)
  }

  def promptTemplates(now: Instant): List[DemoPromptTemplate] =
    prompts.map { prompt =>
      DemoPromptTemplate(
        prompt.id,
        prompt.owner,
        now.minus(prompt.versions.size * 7L + prompt.number, ChronoUnit.DAYS),
        prompt.versions.zipWithIndex.map { case ((title, text), i) =>
          TgPromptTemplateVersionDto(
            DemoSeedIds.id(DemoSeedIds.PromptTemplate, prompt.number * 10 + i + 100),
            prompt.id,
            title,
            text,
            now.minus((prompt.versions.size - i) * 7L + prompt.number, ChronoUnit.DAYS)
          )
        }
      )
    }

  def actions(now: Instant): List[DemoAiAction] =
    actionSpecs.map { action =>
      val created = now.minus(30L - action.number * 4L, ChronoUnit.DAYS)
      DemoAiAction(
        action.id,
        action.owner,
        action.name,
        action.prompt,
        action.promptTemplateId,
        action.model,
        List(MilestoneSummaryEndpoint),
        created,
        created
      )
    }

  lazy val defaults: List[DemoAiActionDefault] =
    defaultSpecs.zipWithIndex.map { case ((projectId, actionId), i) =>
      DemoAiActionDefault(
        DemoSeedIds.id(DemoSeedIds.AiActionDefault, i + 1),
        DemoIdentity.AccountId,
        projectId,
        MilestoneSummaryEndpoint,
        actionId
      )
    }

  private lazy val weeklyPrompts = List(
    "Weekly status report" -> "Summarise the log entries of the period as a short status report for the team.",
    "Weekly status report" ->
      "Summarise the log entries of the period as a status report for the team.\nGroup the work by task, mention blockers and finish with the next steps."
  )

  private lazy val prompts = List(
    PromptSpec(1, DemoIdentity.AccountId, weeklyPrompts),
    PromptSpec(
      2,
      DemoIdentity.AccountId,
      List(
        "Executive summary" ->
          "Write a summary for stakeholders in at most five sentences. Focus on outcomes and risks, not on technical details."
      )
    ),
    PromptSpec(
      3,
      DemoIdentity.AccountId,
      List("Risks and blockers" -> "List everything that slowed the team down in the period and what could still go wrong.")
    ),
    PromptSpec(
      4,
      DemoIdentity.SecondAccountId,
      List("Sprint recap" -> "Recap the sprint in a friendly tone, one paragraph per milestone.")
    )
  )

  private lazy val actionSpecs = List(
    ActionSpec(1, DemoIdentity.AccountId, "Weekly summary", "Use a neutral tone and bullet points.", Some(prompts(0).id), "gpt-4.1-mini"),
    ActionSpec(2, DemoIdentity.AccountId, "Executive brief", "Keep it under 120 words.", Some(prompts(1).id), "gpt-4.1"),
    ActionSpec(3, DemoIdentity.AccountId, "Risk review", "Highlight the three biggest risks first.", Some(prompts(2).id), "gpt-4o"),
    ActionSpec(
      4,
      DemoIdentity.AccountId,
      "Quick recap",
      "Summarise the log entries of the period in a few bullet points.",
      None,
      "gpt-4.1-nano"
    ),
    ActionSpec(5, DemoIdentity.SecondAccountId, "Sprint recap", "Friendly tone please.", Some(prompts(3).id), "gpt-4o-mini")
  )

  private lazy val defaultSpecs = List(
    DemoIdentity.ProjectId -> DemoSeedIds.id(DemoSeedIds.AiAction, 1),
    DemoIdentity.SecondProjectId -> DemoSeedIds.id(DemoSeedIds.AiAction, 4)
  )
}
