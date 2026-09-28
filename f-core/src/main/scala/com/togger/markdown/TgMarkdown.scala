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
  *
  * `allowRawHtml` decides what happens to raw HTML in the source: escaped and shown as text by default, so
  * markdown written by one user cannot inject markup into another user's page, or inserted as markup when it is
  * turned on — only safe for markdown the viewer wrote themselves. [[toHtml]] always inserts it, since that is
  * the library's own behaviour.
  */
class TgMarkdown(config: MarkdownConfig = MarkdownConfig.default, allowRawHtml: Boolean = false) {

  private lazy val render = new TgMarkdownRender(config, allowRawHtml)

  def toHtml(markdown: String): String = Markdown.renderToHTML(markdown, config)

  /** The markdown's top-level blocks as Laminar elements, ready to splice into a component. */
  def toLaminar(markdown: String): Seq[HtmlElement] = render.blocks(parse(markdown))

  /** Same as [[toLaminar]], wrapped in a single `<div>`. */
  def toLaminarDiv(markdown: String): HtmlDiv = div(toLaminar(markdown))

  def parse(markdown: String): Document = Markdown.parseDocumentContent(markdown, config)
}
