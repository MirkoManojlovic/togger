package com.togger.ui.spinner

import cats.effect.IO
import com.raquo.laminar.api.L.{span, _}
import com.raquo.laminar.nodes.ReactiveHtmlElement
import cats.syntax.apply._
import org.scalajs.dom
import org.scalajs.dom.HTMLDivElement

import scala.concurrent.duration._

class SpinnerService private (
  parentElementForSpinner: HtmlElement,
  spinnerElement: ReactiveHtmlElement[HTMLDivElement],
  onError: Throwable => Unit,
  waitForPaint: Boolean
) {

  private val spinnerState = Var(false)

  private val paint: IO[Unit] = if (waitForPaint) SpinnerService.painted else IO.unit

  def runWithSpinner[A](codeBlock: => IO[A]): IO[A] = {
    IO(show())
      .flatMap(_ => paint)
      .flatMap(_ => codeBlock)
      .flatMap(result => paint.as(result))
      .flatMap(result => IO(result).delayBy(100.millis))
      .onError { case error => IO(onError(error)) }
      .guarantee(IO(hide()))
  }

  def show(): Unit =
    spinnerState.set(true)

  def hide(): Unit =
    spinnerState.set(false)

  parentElementForSpinner.amend(
    child.maybe <-- spinnerState.signal.map(shouldShow => Option.when(shouldShow)(spinnerElement))
  )

}

object SpinnerService {

  /** Completes after the browser painted twice (or after a short timeout, animation frames stop in hidden tabs).
    *
    * Data that is already in memory or in the local storage is read and rendered in one go on the main thread, so
    * without it the spinner is never painted before the work starts and is gone as soon as the page is rendered.
    */
  private val nextFrame: IO[Unit] =
    IO.async_[Unit](callback => dom.window.requestAnimationFrame(_ => callback(Right(()))): Unit)

  private val painted: IO[Unit] =
    (nextFrame *> nextFrame).timeoutTo(150.millis, IO.unit)

  private val overlayClasses =
    "flex flex-col items-center justify-center gap-4 z-[200] bg-white/80 dark:bg-gray-900/80 backdrop-blur-sm"

  def spinerElement: ReactiveHtmlElement[HTMLDivElement] =
    div(
      cls("flex flex-col items-center gap-4"),
      div(
        cls("relative w-16 h-16"),
        div(cls("absolute inset-0 rounded-full border-4 border-gray-200 dark:border-gray-700")),
        div(
          cls(
            "absolute inset-0 rounded-full border-4 border-transparent border-t-blue-600 border-r-blue-400 " +
              "dark:border-t-blue-400 dark:border-r-blue-600 animate-spin"
          )
        )
      ),
      span(cls("text-sm font-medium tracking-wide text-gray-600 dark:text-gray-300 animate-pulse"), "Loading..."),
      role("status")
    )

  def globalSpinner(
    parentElementForSpinner: HtmlElement,
    onError: Throwable => Unit,
    waitForPaint: Boolean = false
  ): SpinnerService = {
    val spinnerElement = div(cls(s"fixed inset-0 w-screen h-screen $overlayClasses"), spinerElement)
    new SpinnerService(parentElementForSpinner, spinnerElement, onError, waitForPaint)
  }

  def localSpinner(
    parentElementForSpinner: HtmlElement,
    onError: Throwable => Unit,
    waitForPaint: Boolean = false
  ): SpinnerService = {
    val spinnerElement = div(cls(s"absolute inset-0 w-full h-full $overlayClasses"), spinerElement)
    new SpinnerService(parentElementForSpinner, spinnerElement, onError, waitForPaint)
  }
}
