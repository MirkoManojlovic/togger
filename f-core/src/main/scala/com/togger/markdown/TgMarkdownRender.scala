package com.togger.markdown

import com.raquo.laminar.api.L._
import com.raquo.laminar.codecs.StringAsIsCodec
import com.raquo.laminar.modifiers.KeySetter.StyleSetter
import com.raquo.laminar.modifiers.Modifier
import com.raquo.laminar.nodes.TextNode
import com.togger.ui.auth.TgHtmlTypes.{HtmlLi, HtmlTableCell, HtmlTableRow}
import io.github.edadma.markdown._
import io.github.edadma.markdown.{Image => MarkdownImage}
import io.github.edadma.markdown.{`renderToHTML$package` => Markdown}

/** Renders a parsed markdown [[Document]] as Tg components, instead of going through the library's HTML string
  * renderer.
  *
  * This walk owns the markdown semantics — which node becomes which component, tight-list flattening, the code
  * block's trailing newline, the raw-HTML policy — and [[TgMarkdownStyles]] owns what each one looks like.
  *
  * With `allowRawHtml` off, raw HTML in the source is escaped and shown as text, so markdown written by one user
  * cannot inject markup into another user's page. Turning it on reproduces what `TgMarkdown.toHtml` does — the
  * markup is inserted as-is — and is only safe for markdown the viewer wrote themselves.
  */
class TgMarkdownRender(
  config: MarkdownConfig,
  allowRawHtml: Boolean,
  styles: TgMarkdownStyles,
  onTaskToggle: Option[Observer[TaskToggle]] = None
) {

  /** Numbers the document's task items as the walk meets them. Reset per call, so each render counts from zero —
    * which also means `blocks` is not reentrant. Safe here: ScalaJS is single-threaded and the walk is synchronous.
    */
  private var nextTaskIndex = 0

  def blocks(document: Document): Seq[HtmlElement] = {
    nextTaskIndex = 0
    document.children.map(block)
  }

  private def block(node: Block): HtmlElement = node match {
    case Paragraph(is)             => TgMarkdownParagraph(inlineContent(is), styles).element
    case Heading(level, is, attrs) => TgMarkdownHeading(level, attributes(attrs) ++ inlineContent(is), styles).element
    case BlockQuote(children)      => TgMarkdownBlockQuote(children.map(block), styles).element
    case c: Code                   => codeBlock(c)
    case ListBlock(data, items)    => list(data, items, nested = false)
    case item: ListItem            => listItem(item, tight = false)
    case HTMLBlock(content)        => TgMarkdownRawHtmlBlock(content, allowRawHtml, styles).element
    case Table(header, rows, alignments) =>
      TgMarkdownTable(
        Some(tableRow(header, alignments, header = true)),
        rows.map(tableRow(_, alignments, header = false)),
        styles
      ).element
    case row: TableRow              => tableRow(row, Nil, header = false)
    case TableCell(content)         => tableCell(content, None, header = false)
    case DefinitionListBlock(items) => definitionList(items)
    case MathBlock(content)         => TgMarkdownCodeBlock(content, None, Nil, styles).element
    case CalloutBlock(kind, t, children) => TgMarkdownCallout(kind, t, children.map(block), styles).element
    case CollapsibleBlock(t, isOpen, children) =>
      TgMarkdownCollapsible(inlineContent(t), isOpen, children.map(block), styles).element
    case FootnoteDefinition(label, content) =>
      TgMarkdownFootnote(footnoteId(label), content.map(block), styles).element
    case DocTagBlock(name, target, body, _) => TgMarkdownDocTag(name, target, body.map(block), styles).element
    case _: ThematicBreak                   => TgMarkdownThematicBreak(styles).element
    case other                              => TgMarkdownUnknownBlock(other.toString, styles).element
  }

  /** Mirrors the string renderer: an indented block falls back to the configured language, and non-empty content
    * always ends in a newline.
    */
  private def codeBlock(c: Code): HtmlElement = {
    val language = c.infoString.orElse(if (c.indented) config.indentedCodeLanguage else None)
    val trailing = if (c.content.nonEmpty) "\n" else ""

    TgMarkdownCodeBlock(c.content + trailing, language, attributes(c.attrs), styles).element
  }

  private def list(data: ListData, items: List[ListItem], nested: Boolean): HtmlElement =
    TgMarkdownList(
      ordered = data.isOrdered,
      start = data.startNumber.filter(_ != 1),
      nested = nested,
      items = items.map(item => listItem(item, data.isTight)),
      styles = styles
    ).element

  /** The item's index is taken before its content is rendered, so a task list nested inside a task item numbers
    * after its parent — the order the source has them in, and the order [[TgMarkdownTasks]] scans them in.
    */
  private def listItem(item: ListItem, tight: Boolean): HtmlLi = taskMarker(item) match {
    case Some(isChecked) =>
      val index = nextTaskIndex

      nextTaskIndex += 1
      TgMarkdownTaskListItem(isChecked, toggleObserver(index), itemContent(withoutMarker(item), tight), styles).element

    case None => TgMarkdownListItem(itemContent(item, tight), styles).element
  }

  /** The markdown library models no task item — `ListItem` has no checked flag, and `taskListItems` is read only
    * by its HTML string renderer, never by the parser. The marker is therefore still sitting in the text, and is
    * read back off it the same way `renderToHTML` does, so `toLaminar` and `toHtml` call the same things a task.
    */
  private def taskMarker(item: ListItem): Option[Boolean] =
    if (!config.taskListItems) None
    else
      item.content.headOption
        .collect { case Paragraph(inlines) => inlines }
        .flatMap(_.headOption)
        .collect {
          case Text(text) if text.startsWith("[ ] ")             => false
          case Text(text) if text.toLowerCase.startsWith("[x] ") => true
        }

  private def withoutMarker(item: ListItem): ListItem = item.content match {
    case Paragraph(Text(text) :: rest) :: tail => ListItem(Paragraph(Text(text.drop(4)) :: rest) :: tail)
    case _                                     => item
  }

  private def toggleObserver(index: Int): Option[Observer[Boolean]] =
    onTaskToggle.map(_.contramap[Boolean](checked => TaskToggle(index, checked)))

  /** A tight list item drops the `<p>` around its paragraphs, the way CommonMark renders them — which also keeps
    * the paragraph's bottom margin out of a tight list. A list nested here is the only place one can appear, so
    * this is where the indent gets applied.
    */
  private def itemContent(item: ListItem, tight: Boolean): Seq[Modifier[HtmlElement]] =
    item.content.flatMap {
      case Paragraph(is) if tight   => inlineContent(is)
      case ListBlock(data, entries) => Seq(list(data, entries, nested = true))
      case child                    => Seq(block(child))
    }

  private def definitionList(items: List[(List[Inline], List[Block])]): HtmlElement =
    TgMarkdownDefinitionList(
      items.map { case (term, definitions) => (inlineContent(term), definitions.map(block)) },
      styles
    ).element

  private def tableRow(row: TableRow, alignments: List[TableAlignment], header: Boolean): HtmlTableRow =
    TgMarkdownTableRow(
      row.cells.zipWithIndex.map { case (TableCell(content), index) =>
        tableCell(content, alignments.lift(index).flatMap(alignment), header)
      },
      header,
      styles
    ).element

  private def tableCell(
    content: List[Inline],
    align: Option[StyleSetter],
    header: Boolean
  ): HtmlTableCell =
    TgMarkdownTableCell(align.toSeq ++ inlineContent(content), header, styles).element

  private def alignment(value: TableAlignment): Option[StyleSetter] = value match {
    case TableAlignment.Left   => Some(textAlign("left"))
    case TableAlignment.Center => Some(textAlign("center"))
    case TableAlignment.Right  => Some(textAlign("right"))
    case _                     => None
  }

  private def attributes(attrs: Option[Attributes]): Seq[Modifier[HtmlElement]] =
    attrs.toSeq.flatMap { a =>
      a.id.map(idAttr(_)).toSeq ++
        (if (a.classes.isEmpty) Nil else Seq(cls(a.classes: _*))) ++
        a.kvPairs.map { case (key, value) => htmlAttr(key, StringAsIsCodec) := value }
    }

  /** Inserted raw HTML arrives as separate opening and closing fragments, so a run of inlines containing any of it
    * can only be rendered as one string — element by element the fragments would nest instead of pairing up. It
    * goes into whichever element hosts the run, so it comes out unwrapped. Escaped raw HTML is just text, and
    * needs none of that.
    */
  private def inlineContent(is: List[Inline]): Seq[Modifier[HtmlElement]] =
    if (allowRawHtml && is.exists(_.isInstanceOf[RawHTML])) Seq(rawHtmlContent(Markdown.renderInlines(is)))
    else is.map(inline)

  private def inline(node: Inline): Modifier[HtmlElement] = node match {
    case Text(content)            => new TextNode(content)
    case CodeSpan(content)        => TgMarkdownCodeSpan(content, styles).element
    case Emphasis(is)             => em(cls(styles.emphasis), inlineContent(is))
    case Strong(is)               => strong(cls(styles.strong), inlineContent(is))
    case Strikethrough(is)        => del(cls(styles.strikethrough), inlineContent(is))
    case Link(destination, t, is) => TgMarkdownLink(destination, t, inlineContent(is), styles).element
    case AutoLink(destination, t) => TgMarkdownLink(destination, None, Seq(new TextNode(t)), styles).element
    case i: MarkdownImage         => image(i)
    case RawHTML(content)         => if (allowRawHtml) rawHtml(content) else new TextNode(content)
    case MathExpr(content)        => TgMarkdownCodeSpan(content, styles).element
    case Emoji(name)              => new TextNode(s":$name:")
    case FootnoteReference(label) => TgMarkdownFootnoteRef(label, footnoteId(label), styles).element
    case _: SoftLineBreak         => new TextNode("\n")
    case _: HardLineBreak         => br()
    case other                    => new TextNode(other.toString)
  }

  private def image(i: MarkdownImage): HtmlElement =
    TgMarkdownImage(
      i.destination,
      Markdown.plainText(i.inlines, false),
      i.title,
      attributes(i.attrs),
      styles
    ).element

  private def footnoteId(label: String): String = s"fn-$label"

  private def rawHtml(content: String): HtmlElement = span(rawHtmlContent(content))

  /** The markup is handed to the DOM once the element is mounted — Laminar has no raw-HTML child node. */
  private def rawHtmlContent(content: String): Modifier[HtmlElement] =
    onMountCallback(_.thisNode.ref.innerHTML = content)
}
