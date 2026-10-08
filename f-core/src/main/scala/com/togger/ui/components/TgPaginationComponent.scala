package com.togger.ui.components

import com.raquo.laminar.api.L._
import com.raquo.laminar.codecs.StringAsIsCodec
import com.togger.common.{PageRequest, PaginatedResponse}
import com.togger.ui.auth.TgHtmlTypes.TgHtmlElement
import com.togger.ui.components.pagination.{PageItem, PaginationMath, PaginationState}
import com.togger.ui.organization.TgIcons
import org.scalajs.dom

/** Flowbite "pagination with dropdown": range text, page buttons and an upward-opening page-size listbox. Purely
  * presentational - `onChange(page, size)` is how the screen navigates.
  */
object TgPaginationComponent {

  private val edgeButtonCls =
    "flex items-center justify-center h-9 px-3 text-sm font-medium text-body bg-neutral-secondary-medium " +
      "border border-default-medium hover:bg-neutral-tertiary hover:text-heading focus:outline-none " +
      "focus:ring-2 focus:ring-brand disabled:cursor-not-allowed disabled:opacity-50 " +
      "disabled:hover:bg-neutral-secondary-medium"

  private val pageButtonBaseCls =
    "flex items-center justify-center h-9 w-9 text-sm font-medium border border-default-medium " +
      "focus:outline-none focus:ring-2 focus:ring-brand"

  private val inactivePageCls = "bg-neutral-secondary-medium text-body hover:bg-neutral-tertiary hover:text-heading"
  private val activePageCls = "text-fg-brand bg-neutral-tertiary-medium hover:text-fg-brand"

  def apply(state: Signal[Option[PaginationState]], onChange: (Int, Int) => Unit): HtmlElement =
    div(
      child <-- state.map {
        case Some(current) if current.totalItems > 0 => render(current, onChange)
        case _                                       => emptyNode
      }
    )

  def fromResponse(response: Signal[PaginatedResponse[_]], onChange: (Int, Int) => Unit): HtmlElement =
    apply(response.map(r => Some(PaginationState.fromResponse(r))), onChange)

  private def render(state: PaginationState, onChange: (Int, Int) => Unit): HtmlElement =
    div(
      cls("flex flex-col items-center justify-between gap-3 py-3 md:flex-row"),
      rangeText(state),
      Option.when(state.totalPages > 1)(pageNav(state, onChange)).getOrElse(emptyNode),
      Option
        .when(state.totalItems > PageRequest.SizeOptions.min)(sizeDropdown(state.size, newSize => onChange(1, newSize)))
        .getOrElse(emptyNode)
    )

  private def rangeText(state: PaginationState) =
    span(
      cls("text-sm text-body"),
      "Showing ",
      span(cls("font-semibold text-heading"), s"${state.firstItem}-${state.lastItem}"),
      " of ",
      span(cls("font-semibold text-heading"), state.totalItems.toString)
    )

  private def pageNav(state: PaginationState, onChange: (Int, Int) => Unit) =
    navTag(
      aria.label("Pagination"),
      ul(
        cls("inline-flex -space-x-px text-sm"),
        li(
          button(
            typ("button"),
            cls(s"$edgeButtonCls rounded-s-base"),
            aria.label("Previous page"),
            disabled(!state.hasPrevious),
            TgIcons.chevronLeftIcon("w-4 h-4 rtl:rotate-180"),
            span(cls("ms-1 hidden sm:inline"), "Previous"),
            onClick --> (_ => onChange(state.page - 1, state.size))
          )
        ),
        li(
          cls("flex items-center px-3 h-9 text-sm text-body border border-default-medium md:hidden"),
          s"Page ${state.page} of ${state.totalPages}"
        ),
        PaginationMath.pages(state.page, state.totalPages).map {
          case PageItem.Page(number) => pageItem(number, state, onChange)
          case PageItem.Ellipsis     => ellipsisItem
        },
        li(
          button(
            typ("button"),
            cls(s"$edgeButtonCls rounded-e-base"),
            aria.label("Next page"),
            disabled(!state.hasNext),
            span(cls("me-1 hidden sm:inline"), "Next"),
            TgIcons.chevronRightIcon("w-4 h-4 rtl:rotate-180"),
            onClick --> (_ => onChange(state.page + 1, state.size))
          )
        )
      )
    )

  private def pageItem(number: Int, state: PaginationState, onChange: (Int, Int) => Unit) = {
    val isActive = number == state.page
    li(
      cls("hidden md:block"),
      button(
        typ("button"),
        cls(s"$pageButtonBaseCls ${if (isActive) activePageCls else inactivePageCls}"),
        aria.label(s"Page $number"),
        Option.when(isActive)(aria.current("page")).toList,
        number.toString,
        onClick.filter(_ => !isActive) --> (_ => onChange(number, state.size))
      )
    )
  }

  private def ellipsisItem =
    li(
      cls("hidden md:block"),
      span(
        cls(s"$pageButtonBaseCls bg-neutral-secondary-medium text-body"),
        aria.hidden(true),
        "..."
      )
    )

  private def sizeDropdown(selectedSize: Int, onSelect: Int => Unit): HtmlElement = {
    val open = Var(false)
    val listboxId = s"pagination-size-${PaginationIds.next()}"

    def optionButtons(root: dom.Element): List[dom.HTMLElement] = {
      val nodes = root.querySelectorAll("[role=option]")
      List.tabulate(nodes.length)(i => nodes(i).asInstanceOf[dom.HTMLElement])
    }

    def moveFocus(root: dom.Element, step: Int): Unit = {
      val options = optionButtons(root)
      if (options.nonEmpty) {
        val index = options.indexWhere(_ eq dom.document.activeElement)
        val target =
          if (index >= 0) (index + step + options.size) % options.size
          else if (step > 0) 0
          else options.size - 1
        options(target).focus()
      }
    }

    div(
      cls("relative"),
      onMountBind { ctx =>
        val root = ctx.thisNode.ref
        documentEvents(_.onClick)
          .filter(event => !root.contains(event.target.asInstanceOf[dom.Node]))
          .mapTo(false) --> open
      },
      onMountBind { _ =>
        documentEvents(_.onKeyDown).filter(_.key == "Escape").mapTo(false) --> open
      },
      onKeyDown --> { event =>
        val root = event.currentTarget.asInstanceOf[dom.Element]
        event.key match {
          case "ArrowDown" => event.preventDefault(); open.set(true); moveFocus(root, 1)
          case "ArrowUp"   => event.preventDefault(); open.set(true); moveFocus(root, -1)
          case _           => ()
        }
      },
      button(
        typ("button"),
        cls(
          "inline-flex items-center gap-1 h-9 px-3 text-sm font-medium text-body bg-neutral-secondary-medium " +
            "border border-default-medium rounded-base hover:bg-neutral-tertiary hover:text-heading " +
            "focus:outline-none focus:ring-2 focus:ring-brand"
        ),
        htmlAttr("aria-haspopup", StringAsIsCodec)("listbox"),
        aria.controls(listboxId),
        aria.expanded <-- open.signal,
        s"$selectedSize per page",
        TgIcons.chevronDownIcon("w-4 h-4"),
        onClick --> (_ => open.update(!_))
      ),
      ul(
        idAttr(listboxId),
        role("listbox"),
        aria.label("Items per page"),
        cls(
          "absolute bottom-full end-0 z-10 mb-2 w-36 py-1 text-sm bg-neutral-primary-medium border border-default " +
            "rounded-base shadow-lg"
        ),
        display <-- open.signal.map(if (_) "block" else "none"),
        PageRequest.SizeOptions.map { size =>
          val isSelected = size == selectedSize
          li(
            button(
              typ("button"),
              role("option"),
              aria.selected(isSelected),
              cls(
                s"w-full px-3 py-2 text-start hover:bg-neutral-tertiary focus:bg-neutral-tertiary focus:outline-none " +
                  (if (isSelected) "font-semibold text-fg-brand" else "text-body hover:text-heading")
              ),
              s"$size per page",
              onClick --> { _ =>
                open.set(false)
                if (!isSelected) onSelect(size)
              }
            )
          )
        }
      )
    )
  }

  private object PaginationIds {
    private var counter = 0

    def next(): Int = {
      counter += 1
      counter
    }
  }
}
