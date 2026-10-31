package com.togger.preview

import com.togger.demo.{DemoAiAction, DemoAiActionDefault, DemoPromptTemplate}

/** Seeds prompt templates, AI actions and default actions. */
class AiPreviewSeed(
  data: AiPreviewData,
  prompts: List[DemoPromptTemplate],
  actions: List[DemoAiAction],
  defaults: List[DemoAiActionDefault]
) extends PreviewSeeder {

  override def seed(): Unit = {
    prompts.foreach { prompt =>
      data.promptTemplates.upsert(PreviewPromptTemplate(prompt.id, prompt.createdBy, prompt.createdAt))
      prompt.versions.foreach(data.promptVersions.upsert)
    }
    actions.foreach { a =>
      data.actions.upsert(
        PreviewAiAction(
          a.id,
          a.createdBy,
          a.name,
          a.prompt,
          a.promptTemplateId,
          a.model,
          a.endpoints.map(_.value),
          a.createdAt,
          a.updatedAt
        )
      )
    }
    defaults.foreach { d =>
      data.defaults.upsert(PreviewAiActionDefault(d.id, d.accountId, d.projectId, d.endpoint.value, d.actionId))
    }
  }
}
