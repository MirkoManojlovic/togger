package com.togger.ui.components

import com.raquo.laminar.api.L._
import com.raquo.laminar.modifiers.Modifier
import com.raquo.laminar.nodes.ReactiveHtmlElement
import com.togger.ui.auth.TgHtmlTypes.HtmlDiv
import org.scalajs.dom.HTMLTableCellElement

object TgHtmlTable {

  def createTh(content: String) =
    th(cls("px-6 py-3 font-medium"), content)

  private def createModifierTh(content: Modifier[ReactiveHtmlElement[HTMLTableCellElement]]) =
    th(cls("px-6 py-3 font-medium"), content)

  def createTd(content: Modifier[ReactiveHtmlElement[HTMLTableCellElement]]) =
    td(cls("px-6 py-2"), content)

  def createTdOverflow(content: String) =
    createTd(
      div(
        maxHeight.px(20),
        overflow.hidden,
        content
      )
    )

  private val evenRowClass =
    "odd:bg-neutral-primary even:bg-neutral-secondary-soft border-b border-default"

  private val oddRowClass =
    "odd:bg-neutral-primary even:bg-neutral-secondary-soft border-b border-default"

  def choseRowCls(index: Int): String =
    if (index % 2 == 0) evenRowClass
    else oddRowClass

  def tableElement(headers: Seq[String])(rows: Seq[Seq[ReactiveHtmlElement[HTMLTableCellElement]]]): HtmlDiv =
    div(
      cls("relative overflow-x-auto bg-neutral-primary-soft shadow-xs rounded-base border border-default"),
      table(
        cls("w-full text-sm text-left rtl:text-right text-body"),
        thead(
          cls("text-sm text-body bg-neutral-secondary-soft border-b rounded-base border-default"),
          tr(
            headers.map(createTh)
          )
        ),
        tbody(
          rows.zipWithIndex
            .map { case (rowElement, index) =>
              tr(
                cls(choseRowCls(index)),
                rowElement
              )
            }
        )
      )
    )

  /** Header/rows overload for a table whose header (e.g. an embedded filter control, passed as one of `headers`)
    * must stay mounted while the rows themselves change, rather than being torn down and rebuilt on every row-set
    * update - so `rows` is reactive here while `headers` is built once.
    */
  def tableElement(
    headers: Seq[Modifier[ReactiveHtmlElement[HTMLTableCellElement]]]
  )(rowsSignal: Signal[Seq[Seq[ReactiveHtmlElement[HTMLTableCellElement]]]]): HtmlDiv =
    div(
      cls("relative overflow-x-auto bg-neutral-primary-soft shadow-xs rounded-base border border-default"),
      table(
        cls("w-full text-sm text-left rtl:text-right text-body"),
        thead(
          cls("text-sm text-body bg-neutral-secondary-soft border-b rounded-base border-default"),
          tr(
            headers.map(createModifierTh)
          )
        ),
        tbody(
          children <-- rowsSignal.map(
            _.zipWithIndex
              .map { case (rowElement, index) =>
                tr(
                  cls(choseRowCls(index)),
                  rowElement
                )
              }
          )
        )
      )
    )

}
