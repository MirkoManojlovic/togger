package com.togger.ui.tgprompttemplate

import com.togger.dto.TgPromptTemplateVersionDto

import java.util.UUID

case class TgPromptTemplateForm(
  id: UUID = UUID.randomUUID(),
  title: String = "",
  prompt: String = ""
)

object TgPromptTemplateFormSupport {

  val promptHint: String = "Write the prompt here"

  private val PreviewLength = 80

  /** The first line of the prompt, cut to a length that fits a table cell. */
  def preview(prompt: String): String = {
    val firstLine = prompt.linesIterator.map(_.trim).find(_.nonEmpty).getOrElse("")
    if (firstLine.length <= PreviewLength) firstLine else firstLine.take(PreviewLength - 1) + "…"
  }

  /** Newest version first. */
  def newestFirst(versions: Seq[TgPromptTemplateVersionDto]): Seq[TgPromptTemplateVersionDto] =
    versions.sortBy(_.createdAt).reverse

  /** The last remaining version cannot be deleted, the whole prompt is deleted instead. */
  def canDeleteVersion(versions: Seq[TgPromptTemplateVersionDto]): Boolean = versions.size > 1
}
