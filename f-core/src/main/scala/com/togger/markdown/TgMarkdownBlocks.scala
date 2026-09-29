package com.togger.markdown

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L._
import com.raquo.laminar.codecs.{BooleanAsAttrPresenceCodec, IntAsStringCodec}
import com.raquo.laminar.modifiers.Modifier
import com.raquo.laminar.tags.HtmlTag
import com.togger.ui.auth.TgHtmlTypes._
import org.scalajs.dom.HTMLHeadingElement

/** Block-level markdown nodes as Tg components.
  *
  * Each takes children that are already rendered, so nothing here touches the markdown AST or has to know about
  * the renderer's raw-HTML policy. `content` is a modifier list rather than an element list because the renderer
  * passes it whatever the node needs — rendered inlines, the node's own attributes, or, for inserted raw HTML,
  * a single mount callback.
  */
case class TgMarkdownHeading(level: Int, content: Seq[Modifier[HtmlElement]], styles: TgMarkdownStyles) {

  /** The heading's own classes and any from the `attributes` extension both apply: Laminar merges repeated `cls`
    * modifiers rather than letting the last one win.
    */
  val element: HtmlHeading = TgMarkdownHeading.tag(level)(cls(styles.heading(level)), content)
}

object TgMarkdownHeading {

  def tag(level: Int): HtmlTag[HTMLHeadingElement] = level match {
    case 1 => h1
    case 2 => h2
    case 3 => h3
    case 4 => h4
    case 5 => h5
    case _ => h6
  }
}

case class TgMarkdownParagraph(content: Seq[Modifier[HtmlElement]], styles: TgMarkdownStyles) {
  val element: HtmlParagraph = p(cls(styles.paragraph), content)
}

case class TgMarkdownBlockQuote(children: Seq[HtmlElement], styles: TgMarkdownStyles) {
  val element: HtmlBlockQuote = blockQuote(cls(styles.blockQuote), children)
}

/** @param attrs
  *   modifiers from the `attributes` extension, which the string renderer also puts on the inner `<code>`
  */
case class TgMarkdownCodeBlock(
  content: String,
  language: Option[String],
  attrs: Seq[Modifier[HtmlElement]],
  styles: TgMarkdownStyles
) {

  val element: HtmlPre =
    pre(
      cls(styles.codeBlock),
      code(cls(styles.codeBlockCode), language.map(l => cls(s"language-$l")), attrs, content)
    )
}

/** @param nested
  *   a list inside a list item, which gains the indent that separates it from its parent
  */
case class TgMarkdownList(
  ordered: Boolean,
  start: Option[Int],
  nested: Boolean,
  items: Seq[HtmlLi],
  styles: TgMarkdownStyles
) {

  private val nestedCls = if (nested) cls(styles.nestedList) else emptyMod

  val element: HtmlElement =
    if (ordered) ol(cls(styles.orderedList), nestedCls, start.map(TgMarkdownList.startAttr(_)), items)
    else ul(cls(styles.bulletList), nestedCls, items)
}

object TgMarkdownList {
  val startAttr: HtmlAttr[Int] = htmlAttr("start", IntAsStringCodec)
}

case class TgMarkdownListItem(content: Seq[Modifier[HtmlElement]], styles: TgMarkdownStyles) {
  val element: HtmlLi = li(cls(styles.listItem), content)
}

/** A `- [ ]` / `- [x]` item. The marker has already been taken off `content` by the renderer.
  *
  * @param toggle
  *   `None` renders the checkbox disabled, which is what the markdown library's own HTML renderer emits and the
  *   right default for a rendering of someone else's text. `Some` makes it live.
  */
case class TgMarkdownTaskListItem(
  isChecked: Boolean,
  toggle: Option[Observer[Boolean]],
  content: Seq[Modifier[HtmlElement]],
  styles: TgMarkdownStyles
) {

  val element: HtmlLi =
    li(
      cls(styles.listItem),
      cls(styles.taskListItem),
      input(
        L.typ("checkbox"),
        cls(styles.taskCheckbox),
        L.disabled(toggle.isEmpty),
        TgMarkdownTaskListItem.checkedAttr(isChecked),
        // Controlled against a constant: the box shows what the *source* says, so a click the rewrite declines
        // snaps back instead of leaving the DOM claiming something the document does not. A click that does
        // change the source arrives back as a re-render carrying a new element.
        toggle.map(observer => controlled(L.checked <-- Val(isChecked), onClick.mapToChecked --> observer))
      ),
      content
    )
}

object TgMarkdownTaskListItem {

  /** The content attribute, not Laminar's `checked` *prop*. A prop assigns `element.checked`, which leaves no
    * trace in the markup — and the markup is what `MarkdownDomSerializer` reads to put `- [x] ` back.
    */
  val checkedAttr: HtmlAttr[Boolean] = htmlAttr("checked", BooleanAsAttrPresenceCodec)
}

case class TgMarkdownThematicBreak(styles: TgMarkdownStyles) {
  val element: HtmlHr = hr(cls(styles.thematicBreak))
}

/** The wrapper `<div>` is what scrolls a wide table, so the component owns it rather than the caller.
  *
  * Not routed through `TgHtmlTable.tableElement`: that takes `headers: Seq[String]`, and a markdown header cell
  * holds inlines (`| **a** | [b](…) |`). The classes are lifted from it instead, so markdown tables and the app's
  * own tables still match.
  */
case class TgMarkdownTable(header: Option[HtmlTableRow], rows: Seq[HtmlTableRow], styles: TgMarkdownStyles) {

  val element: HtmlDiv =
    div(
      cls(styles.tableWrapper),
      L.table(
        cls(styles.table),
        header.map(row => thead(cls(styles.tableHead), row)),
        tbody(rows)
      )
    )
}

case class TgMarkdownTableRow(cells: Seq[HtmlTableCell], header: Boolean, styles: TgMarkdownStyles) {

  /** The header row takes its background from `<thead>`, so it skips the striping the body rows carry. */
  val element: HtmlTableRow = tr(if (header) emptyMod else cls(styles.tableRow), cells)
}

case class TgMarkdownTableCell(content: Seq[Modifier[HtmlElement]], header: Boolean, styles: TgMarkdownStyles) {

  val element: HtmlTableCell =
    if (header) th(cls(styles.tableHeaderCell), content)
    else td(cls(styles.tableCell), content)
}

case class TgMarkdownDefinitionList(
  items: Seq[(Seq[Modifier[HtmlElement]], Seq[HtmlElement])],
  styles: TgMarkdownStyles
) {

  val element: HtmlDl =
    dl(
      cls(styles.definitionList),
      items.flatMap { case (term, definitions) =>
        dt(cls(styles.definitionTerm), term) +: definitions.map(d => dd(cls(styles.definitionDescription), d))
      }
    )
}

case class TgMarkdownCallout(
  kind: String,
  title: Option[String],
  children: Seq[HtmlElement],
  styles: TgMarkdownStyles
) {

  val element: HtmlDiv =
    div(cls(styles.callout(kind)), title.map(t => div(cls(styles.calloutTitle), t)), children)
}

case class TgMarkdownCollapsible(
  title: Seq[Modifier[HtmlElement]],
  isOpen: Boolean,
  children: Seq[HtmlElement],
  styles: TgMarkdownStyles
) {

  val element: HtmlElement =
    detailsTag(
      cls(styles.collapsible),
      TgMarkdownCollapsible.openAttr(isOpen),
      summaryTag(cls(styles.collapsibleSummary), title),
      children
    )
}

object TgMarkdownCollapsible {
  val openAttr: HtmlAttr[Boolean] = htmlAttr("open", BooleanAsAttrPresenceCodec)
}

case class TgMarkdownFootnote(id: String, content: Seq[HtmlElement], styles: TgMarkdownStyles) {
  val element: HtmlDiv = div(idAttr(id), cls(styles.footnote), content)
}

/** `doc-tag-<name>` stays on the element as a hook for whatever consumes doc tags. It is not a Tailwind class —
  * an interpolated name could never be scanned — so the visual styling comes from `styles.docTag`.
  */
case class TgMarkdownDocTag(
  name: String,
  target: Option[String],
  body: Seq[HtmlElement],
  styles: TgMarkdownStyles
) {

  val element: HtmlDiv =
    div(
      cls(styles.docTag),
      cls(s"doc-tag doc-tag-$name"),
      target.map(t => strong(cls(styles.strong), t)),
      body
    )
}

/** An `HTMLBlock`. Laminar has no raw-HTML child node, so inserted markup gains this wrapping `<div>`, which the
  * library's string renderer does not emit. Escaped markup is plain text and needs no wrapper of its own, but
  * keeps one so both modes produce the same shape.
  */
case class TgMarkdownRawHtmlBlock(content: String, allowRawHtml: Boolean, styles: TgMarkdownStyles) {

  val element: HtmlDiv =
    if (allowRawHtml) div(cls(styles.rawHtmlBlock), onMountCallback(_.thisNode.ref.innerHTML = content))
    else div(cls(styles.rawHtmlBlock), content)
}

/** Whatever the parser produced that has no component of its own, rendered as its own description rather than
  * dropped, so an unhandled node shows up instead of vanishing.
  */
case class TgMarkdownUnknownBlock(description: String, styles: TgMarkdownStyles) {
  val element: HtmlDiv = div(cls(styles.fallback), description)
}
