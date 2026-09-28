package com.togger.markdown

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L._
import com.raquo.laminar.modifiers.Modifier
import com.togger.ui.auth.TgHtmlTypes.{HtmlAnchor, HtmlImage}

/** Inline markdown nodes that carry structure of their own — a tag choice, an attribute, a wrapper.
  *
  * `Emphasis`, `Strong` and `Strikethrough` have none of that: they are a tag and a class, and the renderer
  * applies them directly from [[TgMarkdownStyles]] rather than through a component whose whole body would be
  * `em(cls(styles.emphasis), content)`.
  */
case class TgMarkdownLink(
  destination: String,
  title: Option[String],
  content: Seq[Modifier[HtmlElement]],
  styles: TgMarkdownStyles
) {

  val element: HtmlAnchor = a(cls(styles.link), href(destination), title.map(L.title(_)), content)
}

case class TgMarkdownImage(
  destination: String,
  altText: String,
  title: Option[String],
  attrs: Seq[Modifier[HtmlElement]],
  styles: TgMarkdownStyles
) {

  val element: HtmlImage =
    img(cls(styles.image), attrs, src(destination), L.alt(altText), title.map(L.title(_)))
}

case class TgMarkdownCodeSpan(content: String, styles: TgMarkdownStyles) {
  val element: HtmlElement = code(cls(styles.codeSpan), content)
}

case class TgMarkdownFootnoteRef(label: String, id: String, styles: TgMarkdownStyles) {
  val element: HtmlElement = sup(a(cls(styles.footnoteRef), href(s"#$id"), label))
}
