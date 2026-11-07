package com.togger.ui.synccheck

import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveHtmlElement
import com.togger.components.button.TgHtmlButton
import com.togger.style.TgCommonStyleJs.heightAndWidthFitContent
import com.togger.style.TgCommonStyleJs.justifyCenterFlex
import com.togger.ui.auth.TgCard
import com.togger.ui.auth.TgInput.tgInputStyle
import com.togger.ui.toast.Toast

class SyncCheckSettingsComponent(settings: SyncIntervalSettingsStore, toast: Toast) {

  private val draft: Var[String] = Var(settings.currentSeconds.toString)

  private val draftSeconds: Signal[Option[Int]] =
    draft.signal.map(_.toIntOption.filter(_ >= SyncIntervalSettingsStore.MinIntervalSeconds))

  private val inputError: Signal[Option[String]] =
    draftSeconds.map(
      _.fold(
        Option(s"Enter a whole number of seconds, at least ${SyncIntervalSettingsStore.MinIntervalSeconds}")
      )(_ => None)
    )

  private val canSave: Signal[Boolean] =
    draftSeconds.combineWith(settings.intervalSeconds).map { case (seconds, saved) => seconds.exists(_ != saved) }

  private def save(): Unit =
    draft.now().toIntOption.filter(_ >= SyncIntervalSettingsStore.MinIntervalSeconds).foreach { seconds =>
      settings.set(seconds)
      toast.success(s"Sync interval saved: $seconds seconds")
    }

  private def reset(): Unit = {
    settings.reset()
    draft.set(settings.currentSeconds.toString)
    toast.success("Sync interval reset to the default")
  }

  private val intervalInput =
    div(
      label(
        cls("block mb-2 text-sm font-medium text-heading"),
        forId("sync-check-interval"),
        "Sync interval (seconds)"
      ),
      input(
        idAttr("sync-check-interval"),
        `type`("number"),
        minAttr(SyncIntervalSettingsStore.MinIntervalSeconds.toString),
        cls(tgInputStyle),
        controlled(
          value <-- draft.signal,
          onInput.mapToValue --> draft.writer
        )
      ),
      child.maybe <-- inputError.map(_.map(message => p(cls("mt-2 text-sm text-red-600"), message))),
      p(
        cls("mt-2 text-sm text-body"),
        "How often the app runs a sync by itself: it imports and then exports all your projects, like the sync " +
          "button. The button shows when it is running."
      )
    )

  def element: ReactiveHtmlElement[org.scalajs.dom.html.Div] =
    div(
      justifyCenterFlex,
      TgCard
        .headerNoFooter(
          header = TgCard.CardHeader.plainText("Sync", TgCard.CardHeaderSize.H3),
          body = TgCard.HtmlCardContent(
            div(
              intervalInput.amend(cls("mb-4")),
              div(
                cls("flex justify-end gap-2"),
                TgHtmlButton
                  .extraSmall("Reset to default")
                  .amend(onClick.mapToUnit --> (_ => reset())),
                TgHtmlButton
                  .extraSmall("Save")
                  .amend(
                    disabled <-- canSave.map(!_),
                    onClick.mapToUnit --> (_ => save())
                  )
              )
            )
          )
        )
        .element
        .amend(heightAndWidthFitContent)
    )

}
