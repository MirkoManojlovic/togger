package com.togger.ui.auth

import com.raquo.airstream.core.Signal
import com.raquo.laminar.api.L.HtmlElement
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.scalajs.dom._
import org.scalajs.dom.html.Paragraph

object TgHtmlTypes {
  type HtmlDiv = ReactiveHtmlElement[HTMLDivElement]
  type HtmlParagraph = ReactiveHtmlElement[Paragraph]
  type HtmlLi = ReactiveHtmlElement[HTMLLIElement]
  type HtmlUl = ReactiveHtmlElement[HTMLUListElement]
  type HtmlAnchor = ReactiveHtmlElement[HTMLAnchorElement]
  type HtmlButton = ReactiveHtmlElement[HTMLButtonElement]
  type HtmlHeading = ReactiveHtmlElement[HTMLHeadingElement]
  type TgHtmlElement = ReactiveHtmlElement[html.Element]

  type HtmlForm = ReactiveHtmlElement[HTMLFormElement]
}

object TgAirstreamTypes {
  type WeakSignal[A] = Signal[Option[A]]
  type WeakSignalHtmlEl = Signal[Option[HtmlElement]]
}
