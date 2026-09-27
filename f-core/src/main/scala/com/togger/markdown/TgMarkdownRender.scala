package com.togger.markdown

import com.raquo.laminar.api.L._
import com.raquo.laminar.codecs.{BooleanAsAttrPresenceCodec, IntAsStringCodec, StringAsIsCodec}
import com.raquo.laminar.modifiers.KeySetter.StyleSetter
import com.raquo.laminar.modifiers.Modifier
import com.raquo.laminar.nodes.{ReactiveHtmlElement, TextNode}
import com.raquo.laminar.tags.HtmlTag
import com.togger.ui.auth.TgHtmlTypes.HtmlLi
import io.github.edadma.markdown._
import io.github.edadma.markdown.{Image => MarkdownImage}
import io.github.edadma.markdown.{`renderToHTML$package` => Markdown}
import org.scalajs.dom.{HTMLElement, HTMLHeadingElement, HTMLTableCellElement}

/** Renders a parsed markdown [[Document]] as Laminar elements, instead of going through the library's HTML string
  * renderer.
  *
  * With `allowRawHtml` off, raw HTML in the source is escaped and shown as text, so markdown written by one user
  * cannot inject markup into another user's page. Turning it on reproduces what `TgMarkdown.toHtml` does — the
  * markup is inserted as-is — and is only safe for markdown the viewer wrote themselves. Laminar has no raw-HTML
  * child node, so an inserted `HTMLBlock` gains a wrapping `<div>` that the string renderer does not emit; raw
  * HTML between inlines is injected into the element holding those inlines and comes out unwrapped.
  */
class TgMarkdownRender(config: MarkdownConfig, allowRawHtml: Boolean) {

  import TgMarkdownRender._

  def blocks(document: Document): Seq[HtmlElement] = document.children.map(block)

  private def block(node: Block): HtmlElement = node match {
    case Paragraph(is)                   => withInlines(p, is)
    case Heading(level, is, attrs)       => withInlines(headingTag(level), is, attributes(attrs))
    case BlockQuote(children)            => blockQuote(children.map(block))
    case c: Code                         => codeBlock(c)
    case ListBlock(data, items)          => list(data, items)
    case item: ListItem                  => listItem(item, tight = false)
    case HTMLBlock(content)              => if (allowRawHtml) rawHtml(div, content) else div(content)
    case Table(header, rows, alignments) => table(tableHeader(header, alignments), tableBody(rows, alignments))
    case row: TableRow                   => tableRow(row, Nil, td)
    case TableCell(content)              => withInlines(td, content)
    case DefinitionListBlock(items)      => dl(items.flatMap(definition))
    case MathBlock(content)              => pre(code(content))
    case CalloutBlock(kind, t, children) => callout(kind, t, children)
    case CollapsibleBlock(t, isOpen, children) =>
      detailsTag(openAttr(isOpen), withInlines(summaryTag, t), children.map(block))
    case FootnoteDefinition(label, content) => div(idAttr(footnoteId(label)), content.map(block))
    case DocTagBlock(name, target, body, _) =>
      div(cls(s"doc-tag doc-tag-$name"), target.map(t => strong(t)), body.map(block))
    case _: ThematicBreak => hr()
    case other            => div(other.toString)
  }

  private def headingTag(level: Int): HtmlTag[HTMLHeadingElement] = level match {
    case 1 => h1
    case 2 => h2
    case 3 => h3
    case 4 => h4
    case 5 => h5
    case _ => h6
  }

  /** Mirrors the string renderer: an indented block falls back to the configured language, and non-empty content
    * always ends in a newline.
    */
  private def codeBlock(c: Code): HtmlElement = {
    val language = c.infoString.orElse(if (c.indented) config.indentedCodeLanguage else None)
    val trailing = if (c.content.nonEmpty) "\n" else ""

    pre(code(attributes(c.attrs), language.map(l => cls(s"language-$l")), c.content + trailing))
  }

  private def list(data: ListData, items: List[ListItem]): HtmlElement = {
    val children = items.map(listItem(_, data.isTight))

    if (data.isOrdered) ol(data.startNumber.filter(_ != 1).map(startAttr(_)), children)
    else ul(children)
  }

  /** A tight list item drops the `<p>` around its paragraphs, the way CommonMark renders them. */
  private def listItem(item: ListItem, tight: Boolean): HtmlLi =
    li(item.content.flatMap {
      case Paragraph(is) if tight => inlines(is)
      case child                  => Seq(block(child))
    })

  private def definition(item: (List[Inline], List[Block])): Seq[HtmlElement] =
    withInlines(dt, item._1) +: item._2.map(d => dd(block(d)))

  private def callout(kind: String, title: Option[String], children: List[Block]): HtmlElement =
    div(cls(s"callout callout-$kind"), title.map(t => strong(t)), children.map(block))

  private def tableHeader(header: TableRow, alignments: List[TableAlignment]): HtmlElement =
    thead(tableRow(header, alignments, th))

  private def tableBody(rows: List[TableRow], alignments: List[TableAlignment]): HtmlElement =
    tbody(rows.map(tableRow(_, alignments, td)))

  private def tableRow(
      row: TableRow,
      alignments: List[TableAlignment],
      cellTag: HtmlTag[HTMLTableCellElement]
  ): HtmlElement =
    tr(row.cells.zipWithIndex.map { case (TableCell(content), index) =>
      withInlines(cellTag, content, alignments.lift(index).flatMap(alignment).toSeq)
    })

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
    * can only be rendered as one string — element by element the fragments would nest instead of pairing up.
    * Escaped raw HTML is just text, and needs none of that.
    */
  private def withInlines[Ref <: HTMLElement](
      tag: HtmlTag[Ref],
      is: List[Inline],
      extra: Seq[Modifier[HtmlElement]] = Nil
  ): ReactiveHtmlElement[Ref] =
    if (allowRawHtml && is.exists(_.isInstanceOf[RawHTML])) tag(extra, rawHtmlContent(Markdown.renderInlines(is)))
    else tag(extra, inlines(is))

  private def inlines(is: List[Inline]): Seq[Modifier[HtmlElement]] = is.map(inline)

  private def inline(node: Inline): Modifier[HtmlElement] = node match {
    case Text(content)            => new TextNode(content)
    case CodeSpan(content)        => code(content)
    case Emphasis(is)             => withInlines(em, is)
    case Strong(is)               => withInlines(strong, is)
    case Strikethrough(is)        => withInlines(del, is)
    case Link(destination, t, is) => withInlines(a, is, Seq(href(destination)) ++ t.map(title(_)))
    case AutoLink(destination, t) => a(href(destination), t)
    case i: MarkdownImage         => image(i)
    case RawHTML(content)         => if (allowRawHtml) rawHtml(span, content) else new TextNode(content)
    case MathExpr(content)        => code(content)
    case Emoji(name)              => new TextNode(s":$name:")
    case FootnoteReference(label) => sup(a(href(s"#${footnoteId(label)}"), label))
    case _: SoftLineBreak         => new TextNode("\n")
    case _: HardLineBreak         => br()
    case other                    => new TextNode(other.toString)
  }

  private def image(i: MarkdownImage): HtmlElement =
    img(attributes(i.attrs), src(i.destination), alt(Markdown.plainText(i.inlines, false)), i.title.map(title(_)))

  private def footnoteId(label: String): String = s"fn-$label"

  private def rawHtml[Ref <: HTMLElement](tag: HtmlTag[Ref], content: String): ReactiveHtmlElement[Ref] =
    tag(rawHtmlContent(content))

  /** The markup is handed to the DOM once the element is mounted — Laminar has no raw-HTML child node. */
  private def rawHtmlContent(content: String): Modifier[HtmlElement] =
    onMountCallback(_.thisNode.ref.innerHTML = content)
}

object TgMarkdownRender {
  private val startAttr = htmlAttr("start", IntAsStringCodec)
  private val openAttr = htmlAttr("open", BooleanAsAttrPresenceCodec)
}
