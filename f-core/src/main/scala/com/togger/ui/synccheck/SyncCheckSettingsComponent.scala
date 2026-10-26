package com.togger.ui.synccheck

import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveHtmlElement
import com.togger.components.button.TgHtmlButton
import com.togger.style.TgCommonStyleJs.{heightAndWidthFitContent, justifyCenterFlex}
import com.togger.ui.auth.TgCard
import com.togger.ui.auth.TgInput.tgInputStyle

class SyncCheckSettingsComponent(settings: SyncCheckSettingsStore) {

  private val inputError: Var[Option[String]] = Var(None)

  private val setInterval = Observer[String] { text =>
    text.toIntOption.filter(_ >= SyncCheckSettingsStore.MinIntervalSeconds) match {
      case Some(seconds) =>
        inputError.set(None)
        settings.set(seconds)
      case None =>
        inputError.set(Some(s"Enter a whole number of seconds, at least ${SyncCheckSettingsStore.MinIntervalSeconds}"))
    }
  }

  private val intervalInput =
    div(
      label(
        cls("block mb-2 text-sm font-medium text-heading"),
        forId("sync-check-interval"),
        "Check interval (seconds)"
      ),
      input(
        idAttr("sync-check-interval"),
        `type`("number"),
        minAttr(SyncCheckSettingsStore.MinIntervalSeconds.toString),
        cls(tgInputStyle),
        controlled(
          value <-- settings.intervalSeconds.map(_.toString),
          onInput.mapToValue --> setInterval
        )
      ),
      child.maybe <-- inputError.signal.map(_.map(message => p(cls("mt-2 text-sm text-red-600"), message))),
      p(
        cls("mt-2 text-sm text-body"),
        "How often the app checks whether there is something to import or export. A circle on the Import and " +
          "Export buttons shows that there is. This is kept in this browser only."
      )
    )

  def element: ReactiveHtmlElement[org.scalajs.dom.html.Div] =
    div(
      justifyCenterFlex,
      TgCard
        .headerNoFooter(
          header = TgCard.CardHeader.plainText("Import / export check", TgCard.CardHeaderSize.H3),
          body = TgCard.HtmlCardContent(
            div(
              intervalInput.amend(cls("mb-4")),
              div(
                cls("flex justify-end"),
                TgHtmlButton.extraSmall("Reset to default").amend(onClick.mapToUnit --> (_ => {
                  inputError.set(None)
                  settings.reset()
                }))
              )
            )
          )
        )
        .element
        .amend(heightAndWidthFitContent)
    )

}
