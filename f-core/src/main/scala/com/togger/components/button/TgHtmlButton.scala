package com.togger.components.button

import com.togger.ui.auth.TgHtmlTypes.HtmlButton
import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.scalajs.dom.HTMLButtonElement

object TgHtmlButton {

  val debounceTimeInMillisDefault = 250

  private case class ButtonWithError(
    disabledEvents: EventStream[Boolean],
    debounceTimeInMillis: Int = debounceTimeInMillisDefault
  ) {

    // Emit true for the invalidated form first
    private val first = EventStream.fromValue(true)
    private val $disabledInternal = disabledEvents.debounce(debounceTimeInMillis)
    private val $disabledCombined = EventStream.merge(first, $disabledInternal)

    val element: HtmlButton =
      button(disabled <-- $disabledCombined)
  }

  def primary(name: String): ReactiveHtmlElement[HTMLButtonElement] =
    button(cls(tailwindStandardButton), name)

  def extraSmall(name: String): ReactiveHtmlElement[HTMLButtonElement] =
    button(cls(tailwindExtraSmallButton), name, typ("button"))

  def secondary(
    disabledEvents: EventStream[Boolean],
    name: String,
    debounceTimeInMillis: Int = debounceTimeInMillisDefault
  ): HtmlButton =
    TgHtmlButton
      .withError(disabledEvents, debounceTimeInMillis)
      .amend(
        cls("btn", "btn-secondary"),
        name,
        typ("submit")
      )

  def generic(
    disabledEvents: EventStream[Boolean],
    name: String,
    debounceTimeInMillis: Int = debounceTimeInMillisDefault
  ): HtmlButton =
    TgHtmlButton
      .withError(disabledEvents, debounceTimeInMillis)
      .amend(
        cls(tailwindStandardButton),
        name,
        typ("submit"),
        float.right
      )

  /** The submit button for a form that aligns it to the bottom right corner. */
  def updateSmall(
    disabledEvents: EventStream[Boolean],
    debounceTimeInMillis: Int = debounceTimeInMillisDefault
  ): HtmlButton =
    TgHtmlButton
      .withError(disabledEvents, debounceTimeInMillis)
      .amend(cls(tailwindSmallButton), "Update", typ("submit"))

  /** The submit button for a form that aligns it to the bottom right corner or lays its buttons out in a row. */
  def createSmall(
    disabledEvents: EventStream[Boolean],
    debounceTimeInMillis: Int = debounceTimeInMillisDefault
  ): HtmlButton =
    TgHtmlButton
      .withError(disabledEvents, debounceTimeInMillis)
      .amend(cls(tailwindSmallButton), "Create", typ("submit"))

  def withError(
    disabledEvents: EventStream[Boolean],
    debounceTimeInMillis: Int = debounceTimeInMillisDefault
  ): HtmlButton =
    ButtonWithError(disabledEvents, debounceTimeInMillis).element

  val tailwindStandardButton =
    """
      |text-white bg-brand box-border border border-transparent hover:bg-brand-strong focus:ring-4
      |focus:ring-brand-medium shadow-xs font-medium leading-5 rounded-base text-sm px-4 py-2.5
      |focus:outline-none w-full
      |""".stripMargin

  val tailwindSmallButton =
    """
      |text-white bg-brand box-border border border-transparent hover:bg-brand-strong focus:ring-4
      |focus:ring-brand-medium shadow-xs font-medium leading-5 rounded-base text-xs px-3 py-1.5
      |focus:outline-none
      |""".stripMargin

  val tailwindExtraSmallButton =
    """
      |text-fg-brand bg-neutral-primary border border-brand hover:bg-brand hover:text-white
      |focus:ring-4 focus:ring-brand-subtle font-medium leading-5 rounded-base text-xs px-3
      |py-1.5 focus:outline-none
      |""".stripMargin
}
