package com.togger.ui.components.reorder

import com.raquo.laminar.api.L._
import org.scalajs.dom

/** Drag and drop and up/down arrows for a list of rows, each told apart by a key. The component only reports moves
  * through `onMove(from, insertion)` (see [[ListReorder.moveToInsertion]]); keeping the order is up to the caller.
  */
class RowReorder[K](order: Signal[Seq[K]], orderNow: () => Seq[K], onMove: (Int, Int) => Unit) {

  private val dragKey = Var(Option.empty[K])

  /** The gap a drop would land in, counted before each row: 0 is above the first, the list size below the last. */
  private val dropInsertion = Var(Option.empty[Int])

  private def indexOf(key: K): Int = orderNow().indexOf(key)

  private def clearDrag(): Unit = {
    dragKey.set(None)
    dropInsertion.set(None)
  }

  private def finishDrop(): Unit = {
    (dragKey.now(), dropInsertion.now()) match {
      case (Some(key), Some(insertion)) => onMove(indexOf(key), insertion)
      case _                            => ()
    }
    clearDrag()
  }

  def dragModifiers(key: K): Seq[Modifier[HtmlElement]] =
    Seq(
      draggable(true),
      onDragStart --> { event =>
        event.dataTransfer.setData("text/plain", key.toString)
        event.dataTransfer.effectAllowed = dom.DataTransferEffectAllowedKind.move
        dragKey.set(Some(key))
      },
      onDragOver.preventDefault --> { event =>
        val rect = event.currentTarget.asInstanceOf[dom.html.Element].getBoundingClientRect()
        val below = event.clientY > rect.top + rect.height / 2
        if (dragKey.now().isDefined) dropInsertion.set(Some(indexOf(key) + (if (below) 1 else 0)))
      },
      onDrop.preventDefault --> (_ => finishDrop()),
      onDragEnd --> (_ => clearDrag()),
      cls <-- dragKey.signal.map(dragged => if (dragged.contains(key)) "opacity-50" else ""),
      cls <-- dropIndicator(key)
    )

  private def dropIndicator(key: K): Signal[String] = {
    val position = order.map(keys => (keys.indexOf(key), keys.size))
    dropInsertion.signal.combineWith(position).map {
      case (target, index, _) if target.contains(index)                        => "border-t-2 border-t-blue-500"
      case (target, index, size) if target.contains(size) && index == size - 1 => "border-b-2 border-b-blue-500"
      case _                                                                   => ""
    }
  }

  private def moveButton(key: K, label: String, glyph: String, delta: Int): HtmlElement = {
    val disabledSignal = order.map { keys =>
      val index = keys.indexOf(key)
      if (delta < 0) index <= 0 else index >= keys.size - 1
    }
    button(
      `type`("button"),
      cls("px-1.5 text-body-subtle hover:text-heading disabled:opacity-30 disabled:cursor-not-allowed cursor-pointer"),
      aria.label(label),
      title(label),
      disabled <-- disabledSignal,
      glyph,
      onClick.mapToUnit --> { _ =>
        val index = indexOf(key)
        onMove(index, ListReorder.insertionFor(index, delta))
      }
    )
  }

  def controls(key: K, itemName: String = "task"): HtmlElement =
    div(
      cls("flex items-center shrink-0"),
      moveButton(key, s"Move $itemName up", "↑", -1),
      moveButton(key, s"Move $itemName down", "↓", 1),
      span(cls("ps-1.5 text-body-subtle cursor-grab select-none"), title("Drag to reorder"), "⠿")
    )
}
