package com.togger.ui.timebox.gantt

import com.raquo.laminar.api.L._
import org.scalajs.dom

import java.time.LocalDate
import scala.scalajs.js

/** Makes a Gantt bar draggable: its ends resize it and its body moves it. While dragging only the bar's element is
  * changed, so the grab is not lost to a redraw; the new dates are handed over once the pointer is let go.
  */
object GanttBarDrag {

  /** Where a drag began: the pointer, the width of a column and the bar as it was. */
  private case class Drag(startX: Double, columnWidth: Double, bar: GanttBar.Placed)

  private def columnStyle(bar: GanttBar.Placed): String = s"${bar.startCol + 2} / span ${bar.width}"

  /** The bar made editable. A clipped bar can't be moved, as moving needs both real dates in sight, and a clipped end
    * can't be resized, as its real date is out of sight.
    */
  def editable(
    layout: GanttChartLayout,
    name: String,
    start: LocalDate,
    due: LocalDate,
    axis: GanttAxis,
    placed: GanttBar.Placed,
    onChange: (LocalDate, LocalDate) => Unit
  )(bar: HtmlElement): HtmlElement = {
    def modifiers(edge: GanttEdge) = dragModifiers(layout, name, start, due, placed, axis, edge, onChange)
    def handle(edge: GanttEdge) = {
      val side = if (edge == GanttEdge.Start) "left-0" else "right-0"
      div(cls(s"absolute top-0 $side h-full w-2 cursor-ew-resize hover:bg-white/30"), modifiers(edge))
    }
    bar.amend(
      if (placed.clippedLeft || placed.clippedRight) emptyMod
      else Seq[Modifier[HtmlElement]](cls("cursor-grab select-none")) ++ modifiers(GanttEdge.Body),
      if (placed.clippedLeft) emptyNode else handle(GanttEdge.Start),
      if (placed.clippedRight) emptyNode else handle(GanttEdge.End)
    )
  }

  private def dragModifiers(
    layout: GanttChartLayout,
    name: String,
    start: LocalDate,
    due: LocalDate,
    placed: GanttBar.Placed,
    axis: GanttAxis,
    edge: GanttEdge,
    onChange: (LocalDate, LocalDate) => Unit
  ): Seq[Modifier[HtmlElement]] = {
    val drag = Var(Option.empty[Drag])

    def barOf(event: dom.PointerEvent): dom.html.Element = {
      val grabbed = event.currentTarget.asInstanceOf[dom.html.Element]
      if (edge == GanttEdge.Body) grabbed else grabbed.parentElement.asInstanceOf[dom.html.Element]
    }

    def resized(event: dom.PointerEvent, state: Drag): GanttBar.Placed = {
      val deltaCols = math.round((event.clientX - state.startX) / state.columnWidth).toInt
      GanttResize.resize(state.bar, edge, deltaCols, axis)
    }

    def show(bar: dom.html.Element, shown: GanttBar.Placed, shownStart: LocalDate, shownDue: LocalDate): Unit = {
      bar.style.setProperty("grid-column", columnStyle(shown))
      bar.title = layout.barTitle(name, shownStart, shownDue)
    }

    def datesFor(shown: GanttBar.Placed): (LocalDate, LocalDate) =
      GanttResize.newDates(start, due, edge, shown, axis).getOrElse((start, due))

    Seq(
      styleAttr := "touch-action: none",
      onPointerDown --> { event =>
        event.stopPropagation()
        event.currentTarget.asInstanceOf[js.Dynamic].setPointerCapture(event.pointerId): Unit
        val columnWidth = barOf(event).getBoundingClientRect().width / placed.width
        drag.set(Some(Drag(event.clientX, columnWidth, placed)))
      },
      onPointerMove --> { event =>
        drag.now().foreach { state =>
          val shown = resized(event, state)
          val (shownStart, shownDue) = datesFor(shown)
          show(barOf(event), shown, shownStart, shownDue)
        }
      },
      onPointerUp --> { event =>
        drag.now().foreach { state =>
          drag.set(None)
          val (newStart, newDue) = datesFor(resized(event, state))
          if ((newStart, newDue) != ((start, due))) onChange(newStart, newDue)
          else show(barOf(event), state.bar, start, due)
        }
      },
      onPointerCancel --> { event =>
        drag.now().foreach(state => show(barOf(event), state.bar, start, due))
        drag.set(None)
      }
    )
  }
}
