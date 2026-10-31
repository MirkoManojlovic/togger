package com.togger.ui.tgprompttemplate

import cats.effect.IO
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.{PageRequest, PaginatedResponse}
import com.togger.dto._
import com.togger.preview._

import java.util.UUID

class TgPromptTemplateFeServicePreview(ctx: PreviewContext, data: AiPreviewData) extends TgPromptTemplateFeService {

  private val NotFoundMessage = "Prompt not found"
  private val VersionNotFoundMessage = "Prompt version not found"
  private val LastVersionMessage = "Cannot delete the only remaining version of a prompt, delete the prompt instead"

  override def createTgPromptTemplate(dto: CreateTgPromptTemplateReq): TgRespIO[FindTgPromptTemplateResp] =
    PreviewResponses.authenticated(ctx.loginRepo)(callerId =>
      IO.defer {
        val template = data.promptTemplates.upsert(
          PreviewPromptTemplate(UUID.randomUUID(), callerId, ctx.clock.currentInstant)
        )
        data.promptVersions.upsert(newVersion(template.id, dto.title, dto.prompt))
        PreviewResponses.ok(toDto(template))
      }
    )

  override def saveNewVersion(dto: NewTgPromptTemplateVersionReq): TgRespIO[FindTgPromptTemplateResp] =
    owned(dto.id) { (_, template) =>
      val unchanged = data.latestVersion(template.id).exists(v => v.title == dto.title && v.prompt == dto.prompt)
      if (!unchanged) data.promptVersions.upsert(newVersion(template.id, dto.title, dto.prompt))
      PreviewResponses.ok(toDto(template))
    }

  override def findAll(page: PageRequest): TgRespIO[PaginatedResponse[FindTgPromptTemplateResp]] =
    PreviewResponses.authenticated(ctx.loginRepo)(callerId =>
      IO.defer {
        val templates = data.promptTemplates
          .filter(_.createdBy == callerId)
          .sortBy(template => (template.createdAt.toEpochMilli, template.id.toString))
          .reverse
        PreviewResponses.paged(templates.map(toDto), page)
      }
    )

  override def findTgPromptTemplateById(id: UUID): TgRespIO[FindTgPromptTemplateResp] =
    owned(id)((_, template) => PreviewResponses.ok(toDto(template)))

  override def listVersions(
    id: UUID,
    page: PageRequest
  ): TgRespIO[PaginatedResponse[TgPromptTemplateVersionDto]] =
    owned(id)((_, template) =>
      PreviewResponses.paged(data.promptVersions.filter(_.tgPromptTemplateId == template.id).reverse, page)
    )

  override def deleteVersion(version: UUID): TgRespIO[TgEmptyBody] =
    PreviewResponses.authenticated(ctx.loginRepo)(callerId =>
      IO.defer(data.promptVersions.find(version) match {
        case None => PreviewResponses.badRequest[TgEmptyBody](VersionNotFoundMessage)
        case Some(existing) =>
          data.promptTemplates.find(existing.tgPromptTemplateId).filter(_.createdBy == callerId) match {
            case None => PreviewResponses.badRequest[TgEmptyBody](NotFoundMessage)
            case Some(_) if data.promptVersions.filter(_.tgPromptTemplateId == existing.tgPromptTemplateId).size <= 1 =>
              PreviewResponses.badRequest[TgEmptyBody](LastVersionMessage)
            case Some(_) =>
              data.promptVersions.delete(version)
              PreviewResponses.okEmpty("Prompt version deleted successfully")
          }
      })
    )

  override def deleteTgPromptTemplateById(id: UUID): TgRespIO[TgEmptyBody] =
    owned(id) { (_, template) =>
      data.deletePromptTemplate(template.id)
      PreviewResponses.okEmpty("Prompt deleted successfully")
    }

  private def owned[A](id: UUID)(f: (UUID, PreviewPromptTemplate) => TgRespIO[A]): TgRespIO[A] =
    PreviewResponses.authenticated(ctx.loginRepo)(callerId =>
      IO.defer(
        data.promptTemplates
          .find(id)
          .filter(_.createdBy == callerId)
          .fold(PreviewResponses.badRequest[A](NotFoundMessage))(f(callerId, _))
      )
    )

  private def newVersion(id: UUID, title: String, prompt: String): TgPromptTemplateVersionDto =
    TgPromptTemplateVersionDto(UUID.randomUUID(), id, title, prompt, ctx.clock.currentInstant)

  private def toDto(template: PreviewPromptTemplate): FindTgPromptTemplateResp = {
    val version = data.latestVersion(template.id).get
    FindTgPromptTemplateResp(
      id = template.id,
      title = version.title,
      prompt = version.prompt,
      latestVersion = version.version,
      createdAt = template.createdAt,
      updatedAt = version.createdAt
    )
  }
}
