package com.togger.ui.updater

import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveHtmlElement
import com.togger.style.TgCommonStyleJs.heightFitContent
import com.togger.style.TgCommonStyleJs.justifyCenterFlex
import com.togger.ui.auth.TgCard
import com.togger.ui.components.TgProgressComponent

class UpdaterComponent(store: UpdaterStore) {
  import UpdateState._

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

  private def desktopBody =
    div(
      div(
        cls("flex items-center gap-3"),
        actionButton("Check for updates", store.state.map(s => s == Checking || UpdateState.isBusy(s)))(
          store.check()
        ),
        child.maybe <-- store.state.map {
          case Available(_, _) =>
            Some(actionButton("Install and restart", Val(false))(store.install()))
          case _ => None
        }
      ),
      p(
        cls("mt-3 min-h-10 text-sm text-body tabular-nums"),
        role("status"),
        child.text <-- store.state.map(UpdaterComponent.statusText)
      ),
      child.maybe <-- store.state.map(UpdaterComponent.progressBar),
      child.maybe <-- store.state.map {
        case Available(_, notes) if notes.nonEmpty =>
          Some(
            pre(
              cls("mt-3 p-2 rounded-base bg-neutral-secondary-medium text-sm text-heading whitespace-pre-wrap"),
              notes
            )
          )
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
          body = TgCard.HtmlCardContent(if (store.isAvailableInApp) desktopBody else webBody)
        )
        .element
        // a fixed width: the card must not resize while the download numbers change
        .amend(cls("w-[32rem] max-w-full"), heightFitContent)
    )

}

object UpdaterComponent {
  import UpdateState._

  def statusText(state: UpdateState): String =
    state match {
      case Idle                  => ""
      case Checking              => "Checking for updates..."
      case UpToDate              => "Togger is up to date."
      case Available(version, _) => s"Version $version is available."
      case Downloading(downloaded, total) =>
        val size = UpdateProgressModel.sizeText(downloaded, total)
        UpdateProgressModel
          .percent(downloaded, total)
          .fold(s"Downloading the update ($size)...")(p => s"Downloading the update: $p% ($size)")
      case Stopping        => "Stopping the Togger services..."
      case Installing      => "Installing the update..."
      case Restarting      => "Restarting Togger..."
      case Failed(message) => s"The update failed: $message"
    }

  /** The bar of a running update: filled by the download percent, pulsing while there is no percent to show. */
  def progressBar(state: UpdateState): Option[HtmlElement] =
    state match {
      case UpdateState.Downloading(downloaded, total) =>
        Some(
          UpdateProgressModel.percent(downloaded, total) match {
            case Some(percent) => TgProgressComponent(percent, extraCls = "mt-3").element
            case None          => TgProgressComponent(100, extraCls = "mt-3 animate-pulse").element
          }
        )
      case UpdateState.Stopping | UpdateState.Installing | UpdateState.Restarting =>
        Some(TgProgressComponent(100, extraCls = "mt-3 animate-pulse").element)
      case _ => None
    }
}
