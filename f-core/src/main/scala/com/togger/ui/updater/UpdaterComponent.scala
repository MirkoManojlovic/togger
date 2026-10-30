package com.togger.ui.updater

import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveHtmlElement
import com.togger.style.TgCommonStyleJs.{heightAndWidthFitContent, justifyCenterFlex}
import com.togger.ui.auth.TgCard

import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.scalajs.js.Thenable.Implicits._

class UpdaterComponent {

  private sealed trait UpdateState
  private case object Idle extends UpdateState
  private case object Checking extends UpdateState
  private case object UpToDate extends UpdateState
  private case class Available(version: String, notes: String) extends UpdateState
  private case object Installing extends UpdateState
  private case class Failed(message: String) extends UpdateState

  private val stateVar = Var[UpdateState](Idle)

  // provided by main.js in the desktop app only
  private val updater: Option[js.Dynamic] =
    Option(js.Dynamic.global.window.tgUpdater).filterNot(js.isUndefined).map(_.asInstanceOf[js.Dynamic])

  private def failed(error: Throwable): Unit = stateVar.set(Failed(error.getMessage))

  private def checkForUpdate(updater: js.Dynamic): Unit = {
    stateVar.set(Checking)
    updater.check().asInstanceOf[js.Promise[js.Dynamic]].toFuture.foreach { result =>
      stateVar.set(
        if (js.isUndefined(result) || result == null) UpToDate
        else Available(result.version.asInstanceOf[String], result.notes.asInstanceOf[String])
      )
    }
    ()
  }

  private def install(updater: js.Dynamic): Unit = {
    stateVar.set(Installing)
    updater.install().asInstanceOf[js.Promise[Unit]].toFuture.failed.foreach(failed)
  }

  private def actionButton(text: String, disabledSignal: Signal[Boolean])(onPress: => Unit) =
    button(
      typ("button"),
      cls(
        "text-white bg-brand hover:bg-brand-strong focus:ring-4 focus:ring-brand-medium font-medium rounded-base " +
          "text-sm px-4 py-2.5 focus:outline-none disabled:opacity-50"
      ),
      disabled <-- disabledSignal,
      text,
      onClick --> { _ => onPress }
    )

  private def statusText(state: UpdateState): String =
    state match {
      case Idle                  => ""
      case Checking              => "Checking for updates..."
      case UpToDate              => "Togger is up to date."
      case Available(version, _) => s"Version $version is available."
      case Installing            => "Downloading and installing the update. Togger will restart when it is done."
      case Failed(message)       => s"The update failed: $message"
    }

  private def desktopBody(updater: js.Dynamic) =
    div(
      div(
        cls("flex items-center gap-3"),
        actionButton("Check for updates", stateVar.signal.map(s => s == Checking || s == Installing))(
          checkForUpdate(updater)
        ),
        child.maybe <-- stateVar.signal.map {
          case Available(_, _) =>
            Some(actionButton("Install and restart", Val(false))(install(updater)))
          case _ => None
        }
      ),
      p(cls("mt-3 text-sm text-body"), role("status"), child.text <-- stateVar.signal.map(statusText)),
      child.maybe <-- stateVar.signal.map {
        case Available(_, notes) if notes.nonEmpty =>
          Some(pre(cls("mt-3 p-2 rounded-base bg-neutral-secondary-medium text-sm text-heading whitespace-pre-wrap"), notes))
        case _ => None
      }
    )

  private val webBody = p(cls("text-sm text-body"), "Updates are only available in the Togger desktop app.")

  def element: ReactiveHtmlElement[org.scalajs.dom.html.Div] =
    div(
      justifyCenterFlex,
      TgCard
        .headerNoFooter(
          header = TgCard.CardHeader.plainText("Updates", TgCard.CardHeaderSize.H3),
          body = TgCard.HtmlCardContent(updater.map(desktopBody).getOrElse(webBody))
        )
        .element
        .amend(heightAndWidthFitContent)
    )

}
