package com.togger.ui.auth

import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveHtmlElement
import com.togger.ui.directorypicker.DirectoryPicker
import org.scalajs.dom.html

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** A directory chooser that looks like a Flowbite file input; a click opens the native directory dialog (desktop only).
  */
case class TgDirInput(
  inputSignal: Signal[String],
  inputStateUpdater: Observer[String],
  labelValue: String,
  isRequired: Boolean = true,
  emptyText: String = "No directory selected"
) {

  private val currentPath = Var("")

  private def choose(): Unit =
    DirectoryPicker.pick(currentPath.now()).foreach(_.foreach(path => inputStateUpdater.onNext(path)))

  val htmlElement: ReactiveHtmlElement[html.Div] =
    div(
      cls("relative"),
      inputSignal --> currentPath,
      label(cls(TgDirInput.labelClass), labelValue),
      // the chooser is a button, so this hidden input carries the required check of the form
      input(
        typ("text"),
        cls(TgDirInput.requiredProxyClass),
        tabIndex(-1),
        required(isRequired),
        value <-- inputSignal
      ),
      button(
        typ("button"),
        cls(TgDirInput.boxClass),
        onClick --> (_ => choose()),
        span(cls(TgDirInput.chooseClass), "Browse"),
        span(
          cls(TgDirInput.pathClass),
          child.text <-- inputSignal.map(path => if (path.isEmpty) emptyText else path)
        )
      )
    )
}

object TgDirInput {

  val labelClass = "block mb-2.5 text-sm font-medium text-heading"

  val boxClass =
    """flex w-full items-center overflow-hidden text-left text-sm text-gray-900 border border-gray-300 rounded-lg
      |cursor-pointer bg-gray-50 focus:outline-none focus:ring-primary-500 focus:border-primary-500
      |dark:text-gray-400 dark:bg-gray-700 dark:border-gray-600""".stripMargin.replace("\n", " ")

  val chooseClass =
    """shrink-0 px-4 py-2.5 font-medium text-gray-900 bg-gray-200 hover:bg-gray-300 border-r border-gray-300
      |dark:text-white dark:bg-gray-600 dark:hover:bg-gray-500 dark:border-gray-600""".stripMargin.replace("\n", " ")

  val requiredProxyClass = "absolute bottom-0 left-1/2 h-px w-px opacity-0 pointer-events-none"

  val pathClass = "px-3 truncate"

}
