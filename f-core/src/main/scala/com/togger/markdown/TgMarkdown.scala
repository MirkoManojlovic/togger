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
  *
  * `styles` only reaches the Laminar output. [[toHtml]] goes straight to the library's string renderer and comes
  * back unstyled.
  *
  * `onTaskToggle` makes rendered `- [ ]` checkboxes live: without one they render disabled, which is what the
  * library's HTML renderer emits and the right default for a rendering of someone else's text. With one, ticking
  * a box reports which task item it was, and the caller rewrites the source — see [[TgMarkdownTasks]].
  */
class TgMarkdown(
  config: MarkdownConfig = TgMarkdown.defaultConfig,
  allowRawHtml: Boolean = false,
  styles: TgMarkdownStyles = TgMarkdownStyles.default,
  onTaskToggle: Option[Observer[TaskToggle]] = None
) {

  private lazy val render = new TgMarkdownRender(config, allowRawHtml, styles, onTaskToggle)

  def toHtml(markdown: String): String = Markdown.renderToHTML(markdown, config)

  /** The markdown's top-level blocks as Laminar elements, ready to splice into a component. */
  def toLaminar(markdown: String): Seq[HtmlElement] = render.blocks(parse(markdown))

  /** Same as [[toLaminar]], wrapped in a single `<div>`. */
  def toLaminarDiv(markdown: String): HtmlDiv = div(toLaminar(markdown))

  def parse(markdown: String): Document = Markdown.parseDocumentContent(markdown, config)
}

object TgMarkdown {

  /** CommonMark plus task lists. `MarkdownConfig.default` has `taskListItems` off, so `- [ ]` would render as
    * literal text everywhere in the app; a caller wanting strict CommonMark still passes it explicitly.
    */
  val defaultConfig: MarkdownConfig = MarkdownConfig.default.copy(taskListItems = true)
}
