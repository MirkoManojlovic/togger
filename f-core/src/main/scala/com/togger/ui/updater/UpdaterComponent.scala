package com.togger.ui.updater

import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveHtmlElement
import com.togger.style.TgCommonStyleJs.heightAndWidthFitContent
import com.togger.style.TgCommonStyleJs.justifyCenterFlex
import com.togger.ui.auth.TgCard

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

  private def statusText(state: UpdateState): String =
    state match {
      case Idle                  => ""
      case Checking              => "Checking for updates..."
      case UpToDate              => "Togger is up to date."
      case Available(version, _) => s"Version $version is available."
      case Installing            => "Downloading and installing the update. Togger will restart when it is done."
      case Failed(message)       => s"The update failed: $message"
    }

  private def desktopBody =
    div(
      div(
        cls("flex items-center gap-3"),
        actionButton("Check for updates", store.state.map(s => s == Checking || s == Installing))(
          store.check()
        ),
        child.maybe <-- store.state.map {
          case Available(_, _) =>
            Some(actionButton("Install and restart", Val(false))(store.install()))
          case _ => None
        }
      ),
      p(cls("mt-3 text-sm text-body"), role("status"), child.text <-- store.state.map(statusText)),
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
        .amend(heightAndWidthFitContent)
    )

}
