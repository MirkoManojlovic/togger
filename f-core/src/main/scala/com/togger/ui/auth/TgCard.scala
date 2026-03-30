package com.togger.ui.auth

import com.togger.ui.auth.TgCard.{CardFooter, CardHeader, TgCardContent}
import com.togger.ui.auth.TgHtmlTypes.{HtmlDiv, HtmlHeading, TgHtmlElement}
import com.raquo.laminar.api.L._
import com.raquo.laminar.modifiers.KeySetter.StyleSetter
import enumeratum.{Enum, EnumEntry}

case class TgCard(header: Option[CardHeader], body: TgCardContent, footer: Option[CardFooter] = None) {

//  <div class="">
//    <form action="#">
//      <h5 class="">Sign in to our platform</h5>
//      <div class="mb-4">
//        <label for="email" class="block mb-2.5 text-sm font-medium text-heading">Your email</label>
//        <input type="email" id="email" class="bg-neutral-secondary-medium border border-default-medium text-heading text-sm rounded-base focus:ring-brand focus:border-brand block w-full px-3 py-2.5 shadow-xs placeholder:text-body" placeholder="example@company.com" required />
//      </div>
//      <div>
//        <label for="password" class="block mb-2.5 text-sm font-medium text-heading">Your password</label>
//        <input type="password" id="password" class="bg-neutral-secondary-medium border border-default-medium text-heading text-sm rounded-base focus:ring-brand focus:border-brand block w-full px-3 py-2.5 shadow-xs placeholder:text-body" placeholder="•••••••••" required />
//      </div>
//      <div class="flex items-start my-6">
//        <div class="flex items-center">
//          <input id="checkbox-remember" type="checkbox" value="" class="w-4 h-4 border border-default-medium rounded-xs bg-neutral-secondary-medium focus:ring-2 focus:ring-brand-soft">
//            <label for="checkbox-remember" class="ms-2 text-sm font-medium text-heading">Remember me</label>
//          </div>
//          <a href="#" class="ms-auto text-sm font-medium text-fg-brand hover:underline">Lost Password?</a>
//        </div>
//        <button type="submit" class="text-white bg-brand box-border border border-transparent hover:bg-brand-strong focus:ring-4 focus:ring-brand-medium shadow-xs font-medium leading-5 rounded-base text-sm px-4 py-2.5 focus:outline-none w-full mb-3">Login to your account</button>
//        <div class="text-sm font-medium text-body">Not registered? <a href="#" class="text-fg-brand hover:underline">Create account</a></div>
//      </form>
//    </div>
//

  val cardClass = "w-full bg-neutral-primary-soft p-6 border border-default rounded-base shadow-xs"
  val cardHeadClass = "text-xl font-semibold text-heading mb-6"

  def element: HtmlDiv =
    div(
      cls(cardClass),
      header
        .map(head => Seq(div(cls(cardHeadClass), head.cardHeaderStyle, head.element)))
        .getOrElse[Seq[HtmlDiv]](Seq.empty),
      div(
//        cls("card-body"),
        body.htmlElement(None),
        footer.map(foot => Seq(div(foot.cardFooterStyle, foot.element)))
      )


    )
  def elementOld: HtmlDiv =
    div(
      cls("card"),
      header
        .map(head => Seq(div(cls("card-header"), head.cardHeaderStyle, div(cls("card-title"), head.element))))
        .getOrElse[Seq[HtmlDiv]](Seq.empty),
      div(
        cls("card-body"),
        body.htmlElement(None),
        footer.map(foot => Seq(div(cls("card-footer"), foot.cardFooterStyle, foot.element)))
      ),
      border("0"),
      boxShadow("0 .25rem 1rem rgba(48,55,66,.15)"),
      height.percent(100),
      margin.rem(0.5),
      borderRadius.rem(0.8)
    )

}

object TgCard {

  def headerNoFooter(header: CardHeader, body: TgCardContent): TgCard =
    TgCard(header = Some(header), body)

  def headerAndFooter(header: CardHeader, body: TgCardContent, footer: CardFooter): TgCard =
    TgCard(Some(header), body, Some(footer))

  def noHeaderNoFooter(body: TgCardContent): TgCard =
    TgCard(None, body, None)

  sealed trait TgCardContent {
    def htmlElement(parentOpt: Option[TgHtmlElement]): TgHtmlElement
  }

  case class PlainTextCardContent(text: String) extends TgCardContent {
    override def htmlElement(parentOpt: Option[TgHtmlElement]): TgHtmlElement =
      parentOpt.map(parent => parent.amend(text)).getOrElse(p(text))
  }

  case class HtmlCardContent(element: TgHtmlElement) extends TgCardContent {
    override def htmlElement(parentOpt: Option[TgHtmlElement]): TgHtmlElement =
      parentOpt.map(parent => parent.amend(element)).getOrElse(element)
  }

  sealed trait CardHeaderSize extends EnumEntry {
    def element: HtmlElement
  }

  object CardHeaderSize extends Enum[CardHeaderSize] {
    override def values: IndexedSeq[CardHeaderSize] = findValues

    case object H1 extends CardHeaderSize { override def element: HtmlHeading = h1(marginBottom("0")) }
    case object H2 extends CardHeaderSize { override def element: HtmlHeading = h2(marginBottom("0")) }
    case object H3 extends CardHeaderSize { override def element: HtmlHeading = h3(marginBottom("0")) }
    case object H4 extends CardHeaderSize { override def element: HtmlHeading = h4(marginBottom("0")) }
    case object H5 extends CardHeaderSize { override def element: HtmlHeading = h5(marginBottom("0")) }
    case object H6 extends CardHeaderSize { override def element: HtmlHeading = h6(marginBottom("0")) }

    case object Div extends CardHeaderSize { override def element: HtmlDiv = div(marginBottom("0")) }

  }

  case class CardHeader(
    content: TgCardContent,
    size: CardHeaderSize = CardHeaderSize.H5,
    cardHeaderStyle: Seq[StyleSetter]
  ) {
    def element: TgHtmlElement = content.htmlElement(Some(size.element))
  }
  object CardHeader {

    def plainText(
      content: String,
      size: CardHeaderSize = CardHeaderSize.H5,
      cardHeaderStyle: Seq[StyleSetter] = List.empty
    ): CardHeader =
      CardHeader(PlainTextCardContent(content), size, cardHeaderStyle)

    def html(
      element: TgHtmlElement,
      size: CardHeaderSize = CardHeaderSize.H5,
      cardHeaderStyle: Seq[StyleSetter] = List.empty
    ): CardHeader =
      CardHeader(HtmlCardContent(element), size, cardHeaderStyle)

  }

  case class CardFooter(
    content: TgCardContent,
    cardFooterStyle: Seq[StyleSetter]
  ) {
    def element: HtmlElement = content.htmlElement(None)
  }

  object CardFooter {

    def html(
      element: TgHtmlElement,
      cardFooterStyle: Seq[StyleSetter] = List.empty
    ): CardFooter =
      CardFooter(HtmlCardContent(element), cardFooterStyle)

//    def fromSignal[A <: FrontendPage](
//      paginationSig: Signal[Option[Pagination[A]]],
//      cardFooterStyle: Seq[StyleSetter] = List.empty
//    ): CardFooter = CardFooter.html(
//      div(
//        child.maybe <-- paginationSig.optionMap(_.paginationElement)
//      ),
//      cardFooterStyle = Seq(display.flex, justifyContent.start)
//    )

    def fromSignalElement(
      paginationSig: Signal[Option[HtmlDiv]],
      cardFooterStyle: Seq[StyleSetter] = List.empty
    ): CardFooter = CardFooter.html(
      div(child.maybe <-- paginationSig),
      cardFooterStyle = Seq(display.flex, justifyContent.start)
    )

  }

}
