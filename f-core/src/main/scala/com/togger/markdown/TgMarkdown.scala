package com.togger.markdown

import com.raquo.laminar.api.L._
import com.togger.ui.auth.TgHtmlTypes.HtmlDiv
import io.github.edadma.markdown.{Document, MarkdownConfig}
import io.github.edadma.markdown.{`renderToHTML$package` => Markdown}

/** Thin wrapper over `io.github.edadma.markdown`.
  *
  * The library is Scala 3 and is read through the TASTy reader, which does not expose Scala 3 top-level
  * definitions under their package. They live in a synthetic object named after their source file, hence the
  * `renderToHTML$package` alias above.
  */
object TgMarkdown {

  val defaultConfig: MarkdownConfig = MarkdownConfig.default

  def toHtml(markdown: String, config: MarkdownConfig = defaultConfig): String =
    Markdown.renderToHTML(markdown, config)

  /** The markdown's top-level blocks as Laminar elements, ready to splice into a component.
    *
    * Raw HTML in the source is escaped and shown as text. Pass `allowRawHtml = true` to insert it as markup
    * instead, the way [[toHtml]] does — only for markdown the viewer wrote themselves.
    */
  def toLaminar(
      markdown: String,
      config: MarkdownConfig = defaultConfig,
      allowRawHtml: Boolean = false
  ): Seq[HtmlElement] =
    new TgMarkdownRender(config, allowRawHtml).blocks(parse(markdown, config))

  /** Same as [[toLaminar]], wrapped in a single `<div>`. */
  def toLaminarDiv(
      markdown: String,
      config: MarkdownConfig = defaultConfig,
      allowRawHtml: Boolean = false
  ): HtmlDiv =
    div(toLaminar(markdown, config, allowRawHtml))

  def parse(markdown: String, config: MarkdownConfig = defaultConfig): Document =
    Markdown.parseDocumentContent(markdown, config)
}
